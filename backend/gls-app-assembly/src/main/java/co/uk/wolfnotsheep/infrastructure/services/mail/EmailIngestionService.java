package co.uk.wolfnotsheep.infrastructure.services.mail;

import co.uk.wolfnotsheep.document.events.DocumentIngestedEvent;
import co.uk.wolfnotsheep.document.models.ConnectedDrive;
import co.uk.wolfnotsheep.document.models.DocumentModel;
import co.uk.wolfnotsheep.document.models.DocumentStatus;
import co.uk.wolfnotsheep.document.repositories.DocumentRepository;
import co.uk.wolfnotsheep.document.services.DocumentService;
import co.uk.wolfnotsheep.document.services.ObjectStorageService;
import co.uk.wolfnotsheep.infrastructure.services.mail.GmailService.EmailAttachment;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePartHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

/**
 * Ingests emails from Gmail as documents. Each email becomes a parent document
 * (message/rfc822) and each attachment becomes a child document.
 */
@Service
public class EmailIngestionService {

    private static final Logger log = LoggerFactory.getLogger(EmailIngestionService.class);
    private static final String EXCHANGE = "gls.documents";
    private static final String ROUTING_INGESTED = "document.ingested";

    private final GmailService gmailService;
    private final DocumentRepository documentRepo;
    private final ObjectStorageService objectStorage;
    private final RabbitTemplate rabbitTemplate;
    private final MongoTemplate mongoTemplate;

    public EmailIngestionService(GmailService gmailService,
                                  DocumentRepository documentRepo,
                                  ObjectStorageService objectStorage,
                                  RabbitTemplate rabbitTemplate,
                                  MongoTemplate mongoTemplate) {
        this.gmailService = gmailService;
        this.documentRepo = documentRepo;
        this.objectStorage = objectStorage;
        this.rabbitTemplate = rabbitTemplate;
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * Ingest a single Gmail message and its attachments as documents.
     * Returns the parent email document, or null if already ingested.
     */
    public DocumentModel ingestMessage(ConnectedDrive account, String messageId,
                                        String uploadedBy, String organisationId) {
        // De-dup: check if this message has already been ingested for this account
        boolean exists = mongoTemplate.exists(
                Query.query(Criteria.where("storageProvider").is("GMAIL")
                        .and("externalStorageRef.messageId").is(messageId)
                        .and("externalStorageRef.accountEmail").is(account.getProviderAccountEmail())),
                DocumentModel.class);
        if (exists) {
            log.debug("Message {} already ingested for account {}", messageId, account.getProviderAccountEmail());
            return null;
        }

        try {
            // Fetch full message
            Message message = gmailService.getMessage(account, messageId);

            // Extract headers
            String from = getHeader(message, "From");
            String to = getHeader(message, "To");
            String subject = getHeader(message, "Subject");
            String date = getHeader(message, "Date");

            // Check for encrypted email (S/MIME)
            if (isEncrypted(message)) {
                log.warn("Encrypted email detected: {}, marking as PROCESSING_FAILED", messageId);
                DocumentModel encDoc = createEmailDocument(account, message, from, to, subject, date,
                        "", uploadedBy, organisationId);
                encDoc.setStatus(DocumentStatus.PROCESSING_FAILED);
                encDoc.setLastError("Encrypted email (S/MIME) — cannot extract body text");
                encDoc.setLastErrorStage("TEXT_EXTRACTION");
                return documentRepo.save(encDoc);
            }

            // Extract body text
            String body = gmailService.extractBody(message);
            String fullText = buildEmailText(from, to, subject, date, body);

            // Create parent email document
            DocumentModel emailDoc = createEmailDocument(account, message, from, to, subject, date,
                    fullText, uploadedBy, organisationId);

            // Store body to MinIO
            byte[] bodyBytes = fullText.getBytes(StandardCharsets.UTF_8);
            objectStorage.upload(emailDoc.getStorageKey(),
                    new ByteArrayInputStream(bodyBytes), bodyBytes.length, "message/rfc822");

            emailDoc = documentRepo.save(emailDoc);

            // Publish ingested event for the email body
            publishIngestedEvent(emailDoc, uploadedBy);

            // Extract and ingest attachments
            List<EmailAttachment> attachments = gmailService.extractAttachments(message);
            List<String> childIds = new ArrayList<>();

            for (EmailAttachment attachment : attachments) {
                try {
                    DocumentModel attachDoc = ingestAttachment(account, message.getId(),
                            attachment, emailDoc.getId(), uploadedBy, organisationId);
                    childIds.add(attachDoc.getId());
                } catch (Exception e) {
                    log.error("Failed to ingest attachment {} from message {}: {}",
                            attachment.filename(), messageId, e.getMessage());
                }
            }

            // Update parent with child IDs
            if (!childIds.isEmpty()) {
                emailDoc.setChildDocumentIds(childIds);
                emailDoc = documentRepo.save(emailDoc);
            }

            log.info("Ingested email {} ({}) with {} attachments from {}",
                    messageId, subject, childIds.size(), account.getProviderAccountEmail());
            return emailDoc;

        } catch (Exception e) {
            log.error("Failed to ingest message {} from {}: {}",
                    messageId, account.getProviderAccountEmail(), e.getMessage());
            throw new RuntimeException("Failed to ingest email: " + e.getMessage(), e);
        }
    }

    private DocumentModel createEmailDocument(ConnectedDrive account, Message message,
                                               String from, String to, String subject, String date,
                                               String fullText, String uploadedBy, String organisationId) {
        String sanitizedSubject = sanitizeFilename(from + " - " + subject);
        String storageKey = UUID.randomUUID() + "/" + sanitizedSubject + ".eml";
        String hash = computeSha256(fullText.getBytes(StandardCharsets.UTF_8));

        DocumentModel doc = new DocumentModel();
        doc.setFileName(storageKey);
        doc.setOriginalFileName(sanitizedSubject + ".eml");
        doc.setMimeType("message/rfc822");
        doc.setFileSizeBytes(fullText.getBytes(StandardCharsets.UTF_8).length);
        doc.setSha256Hash(hash);
        doc.setStorageBucket(objectStorage.getDefaultBucket());
        doc.setStorageKey(storageKey);
        doc.setStatus(DocumentStatus.UPLOADED);
        doc.setUploadedBy(uploadedBy);
        doc.setOrganisationId(organisationId);
        doc.setStorageProvider("GMAIL");
        doc.setConnectedDriveId(account.getId());
        doc.setTraits(List.of("EMAIL_BODY"));
        doc.setCreatedAt(Instant.now());
        doc.setUpdatedAt(Instant.now());

        // External storage reference
        Map<String, String> extRef = new HashMap<>();
        extRef.put("messageId", message.getId());
        extRef.put("threadId", message.getThreadId());
        extRef.put("accountEmail", account.getProviderAccountEmail());
        extRef.put("snippet", message.getSnippet() != null ? message.getSnippet() : "");
        extRef.put("gmailUrl", "https://mail.google.com/mail/u/0/#inbox/" + message.getId());
        extRef.put("from", from);
        extRef.put("to", to);
        extRef.put("subject", subject);
        extRef.put("date", date);
        doc.setExternalStorageRef(extRef);

        // Generate slug
        DocumentModel saved = documentRepo.save(doc);
        saved.setSlug(co.uk.wolfnotsheep.document.util.SlugGenerator.generate(
                sanitizedSubject + ".eml", saved.getId()));
        return documentRepo.save(saved);
    }

    private DocumentModel ingestAttachment(ConnectedDrive account, String messageId,
                                            EmailAttachment attachment, String parentDocId,
                                            String uploadedBy, String organisationId) throws Exception {
        byte[] data = gmailService.downloadAttachment(account, messageId, attachment.attachmentId());

        String storageKey = UUID.randomUUID() + "/" + attachment.filename();
        String hash = computeSha256(data);

        // Store to MinIO
        objectStorage.upload(storageKey, new ByteArrayInputStream(data), data.length, attachment.mimeType());

        DocumentModel doc = new DocumentModel();
        doc.setFileName(storageKey);
        doc.setOriginalFileName(attachment.filename());
        doc.setMimeType(attachment.mimeType());
        doc.setFileSizeBytes(data.length);
        doc.setSha256Hash(hash);
        doc.setStorageBucket(objectStorage.getDefaultBucket());
        doc.setStorageKey(storageKey);
        doc.setStatus(DocumentStatus.UPLOADED);
        doc.setUploadedBy(uploadedBy);
        doc.setOrganisationId(organisationId);
        doc.setStorageProvider("GMAIL");
        doc.setConnectedDriveId(account.getId());
        doc.setParentDocumentId(parentDocId);
        doc.setTraits(List.of("EMAIL_ATTACHMENT"));
        doc.setCreatedAt(Instant.now());
        doc.setUpdatedAt(Instant.now());

        // External storage reference
        Map<String, String> extRef = new HashMap<>();
        extRef.put("messageId", messageId);
        extRef.put("attachmentId", attachment.attachmentId());
        extRef.put("accountEmail", account.getProviderAccountEmail());
        doc.setExternalStorageRef(extRef);

        DocumentModel saved = documentRepo.save(doc);
        saved.setSlug(co.uk.wolfnotsheep.document.util.SlugGenerator.generate(
                attachment.filename(), saved.getId()));
        saved = documentRepo.save(saved);

        // Publish ingested event
        publishIngestedEvent(saved, uploadedBy);

        return saved;
    }

    private void publishIngestedEvent(DocumentModel doc, String uploadedBy) {
        try {
            rabbitTemplate.convertAndSend(EXCHANGE, ROUTING_INGESTED, new DocumentIngestedEvent(
                    doc.getId(),
                    doc.getOriginalFileName(),
                    doc.getMimeType(),
                    doc.getFileSizeBytes(),
                    doc.getStorageBucket(),
                    doc.getStorageKey(),
                    uploadedBy,
                    Instant.now(),
                    null
            ));
        } catch (Exception e) {
            log.error("Failed to publish ingested event for doc {}: {}", doc.getId(), e.getMessage());
            doc.setStatus(DocumentStatus.PROCESSING_FAILED);
            doc.setLastError("Failed to publish to processing queue: " + e.getMessage());
            doc.setLastErrorStage("QUEUE");
            documentRepo.save(doc);
        }
    }

    private String buildEmailText(String from, String to, String subject, String date, String body) {
        StringBuilder sb = new StringBuilder();
        sb.append("From: ").append(from).append("\n");
        sb.append("To: ").append(to).append("\n");
        sb.append("Date: ").append(date).append("\n");
        sb.append("Subject: ").append(subject).append("\n");
        sb.append("\n");
        sb.append(body);
        return sb.toString();
    }

    private String getHeader(Message message, String name) {
        if (message.getPayload() != null && message.getPayload().getHeaders() != null) {
            for (MessagePartHeader header : message.getPayload().getHeaders()) {
                if (name.equalsIgnoreCase(header.getName())) return header.getValue();
            }
        }
        return "";
    }

    private boolean isEncrypted(Message message) {
        if (message.getPayload() == null) return false;
        String mimeType = message.getPayload().getMimeType();
        return mimeType != null && (mimeType.contains("pkcs7-mime") || mimeType.contains("pkcs7-signature"));
    }

    private String sanitizeFilename(String name) {
        if (name == null) return "email";
        return name.replaceAll("[^a-zA-Z0-9\\s._-]", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String computeSha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) hexString.append(String.format("%02x", b));
            return hexString.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
