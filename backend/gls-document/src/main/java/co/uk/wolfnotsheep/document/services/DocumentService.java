package co.uk.wolfnotsheep.document.services;

import co.uk.wolfnotsheep.document.models.AuditEvent;
import co.uk.wolfnotsheep.document.models.DocumentModel;
import co.uk.wolfnotsheep.document.models.DocumentStatus;
import co.uk.wolfnotsheep.document.repositories.AuditEventRepository;
import co.uk.wolfnotsheep.document.repositories.DocumentRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final AuditEventRepository auditEventRepository;
    private final ObjectStorageService objectStorage;

    public DocumentService(DocumentRepository documentRepository,
                           AuditEventRepository auditEventRepository,
                           ObjectStorageService objectStorage) {
        this.documentRepository = documentRepository;
        this.auditEventRepository = auditEventRepository;
        this.objectStorage = objectStorage;
    }

    /**
     * Ingests a new document: stores the file in object storage,
     * creates the document record, and returns it for event publishing.
     */
    public DocumentModel ingest(MultipartFile file, String uploadedBy, String organisationId) {
        try {
            String storageKey = generateStorageKey(file.getOriginalFilename());
            String hash = computeSha256(file.getInputStream());

            // Store the raw file
            objectStorage.upload(
                    storageKey,
                    file.getInputStream(),
                    file.getSize(),
                    file.getContentType()
            );

            // Create document record
            DocumentModel doc = new DocumentModel();
            doc.setFileName(storageKey);
            doc.setOriginalFileName(file.getOriginalFilename());
            doc.setMimeType(file.getContentType());
            doc.setFileSizeBytes(file.getSize());
            doc.setSha256Hash(hash);
            doc.setStorageBucket(objectStorage.getDefaultBucket());
            doc.setStorageKey(storageKey);
            doc.setStatus(DocumentStatus.UPLOADED);
            doc.setUploadedBy(uploadedBy);
            doc.setOrganisationId(organisationId);
            doc.setCreatedAt(Instant.now());
            doc.setUpdatedAt(Instant.now());

            DocumentModel saved = documentRepository.save(doc);

            // Audit
            audit(saved.getId(), "DOCUMENT_UPLOADED", uploadedBy, "USER", Map.of(
                    "fileName", file.getOriginalFilename(),
                    "mimeType", file.getContentType(),
                    "size", String.valueOf(file.getSize())
            ));

            return saved;
        } catch (Exception e) {
            throw new RuntimeException("Failed to ingest document", e);
        }
    }

    public DocumentModel getById(String id) {
        return documentRepository.findById(id).orElse(null);
    }

    public Page<DocumentModel> getByUploader(String uploadedBy, Pageable pageable) {
        return documentRepository.findByUploadedBy(uploadedBy, pageable);
    }

    public Page<DocumentModel> getByStatus(DocumentStatus status, Pageable pageable) {
        return documentRepository.findByStatus(status, pageable);
    }

    public Page<DocumentModel> getByOrganisation(String orgId, Pageable pageable) {
        return documentRepository.findByOrganisationId(orgId, pageable);
    }

    public Page<DocumentModel> getReviewQueue(Pageable pageable) {
        return documentRepository.findByStatusIn(
                List.of(DocumentStatus.REVIEW_REQUIRED),
                pageable
        );
    }

    public DocumentModel updateStatus(String id, DocumentStatus newStatus, String performedBy) {
        DocumentModel doc = documentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Document not found: " + id));

        DocumentStatus oldStatus = doc.getStatus();
        doc.setStatus(newStatus);
        doc.setUpdatedAt(Instant.now());

        if (newStatus == DocumentStatus.PROCESSED) doc.setProcessedAt(Instant.now());
        if (newStatus == DocumentStatus.CLASSIFIED) doc.setClassifiedAt(Instant.now());
        if (newStatus == DocumentStatus.GOVERNANCE_APPLIED) doc.setGovernanceAppliedAt(Instant.now());

        DocumentModel saved = documentRepository.save(doc);

        audit(id, "STATUS_CHANGED", performedBy, "SYSTEM", Map.of(
                "from", oldStatus.name(),
                "to", newStatus.name()
        ));

        return saved;
    }

    public DocumentModel save(DocumentModel doc) {
        doc.setUpdatedAt(Instant.now());
        return documentRepository.save(doc);
    }

    public List<DocumentModel> getExpiredDocuments() {
        return documentRepository.findByRetentionExpiresAtBeforeAndLegalHoldFalseAndStatusNot(
                Instant.now(), DocumentStatus.DISPOSED);
    }

    public InputStream downloadFile(String id) {
        DocumentModel doc = documentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Document not found: " + id));
        return objectStorage.download(doc.getStorageBucket(), doc.getStorageKey());
    }

    public Page<AuditEvent> getAuditTrail(String documentId, Pageable pageable) {
        return auditEventRepository.findByDocumentIdOrderByTimestampDesc(documentId, pageable);
    }

    public long countByStatus(DocumentStatus status) {
        return documentRepository.countByStatus(status);
    }

    // ── Helpers ──────────────────────────────────────────

    private void audit(String documentId, String action, String performedBy,
                       String performedByType, Map<String, String> details) {
        auditEventRepository.save(new AuditEvent(documentId, action, performedBy, performedByType, details));
    }

    private String generateStorageKey(String originalName) {
        String ext = "";
        if (originalName != null && originalName.contains(".")) {
            ext = originalName.substring(originalName.lastIndexOf('.'));
        }
        return UUID.randomUUID() + ext;
    }

    private String computeSha256(InputStream is) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[8192];
        int read;
        while ((read = is.read(buffer)) != -1) {
            digest.update(buffer, 0, read);
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
