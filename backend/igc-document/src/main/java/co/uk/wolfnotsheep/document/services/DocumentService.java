package co.uk.wolfnotsheep.document.services;

import co.uk.wolfnotsheep.document.models.AuditEvent;
import co.uk.wolfnotsheep.document.models.DocumentModel;
import co.uk.wolfnotsheep.document.models.DocumentStatus;
import co.uk.wolfnotsheep.document.repositories.AuditEventRepository;
import co.uk.wolfnotsheep.document.repositories.DocumentRepository;
import co.uk.wolfnotsheep.document.util.SlugGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import co.uk.wolfnotsheep.governance.models.SensitivityLabel;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.support.PageableExecutionUtils;

import java.io.InputStream;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    private final DocumentRepository documentRepository;
    private final AuditEventRepository auditEventRepository;
    private final ObjectStorageService objectStorage;
    private final MongoTemplate mongoTemplate;
    private final PipelineStatusNotifier statusNotifier;

    public DocumentService(DocumentRepository documentRepository,
                           AuditEventRepository auditEventRepository,
                           ObjectStorageService objectStorage,
                           MongoTemplate mongoTemplate,
                           PipelineStatusNotifier statusNotifier) {
        this.documentRepository = documentRepository;
        this.auditEventRepository = auditEventRepository;
        this.objectStorage = objectStorage;
        this.mongoTemplate = mongoTemplate;
        this.statusNotifier = statusNotifier;
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
            saved.setSlug(SlugGenerator.generate(file.getOriginalFilename(), saved.getId()));
            saved = documentRepository.save(saved);

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

    public List<DocumentModel> getAll() {
        return documentRepository.findAll();
    }

    /** Find documents by external storage file IDs — efficient indexed query for Drive cross-referencing. */
    public List<DocumentModel> findByExternalFileIds(List<String> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) return List.of();
        return mongoTemplate.find(
                Query.query(Criteria.where("externalStorageRef.fileId").in(fileIds)),
                DocumentModel.class);
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
        if (newStatus == DocumentStatus.GOVERNANCE_APPLIED || newStatus == DocumentStatus.INBOX) doc.setGovernanceAppliedAt(Instant.now());

        DocumentModel saved = documentRepository.save(doc);

        audit(id, "STATUS_CHANGED", performedBy, "SYSTEM", Map.of(
                "from", oldStatus.name(),
                "to", newStatus.name()
        ));

        statusNotifier.notifyStatusChange(id, newStatus.name(), saved.getOriginalFileName());
        return saved;
    }

    public DocumentModel save(DocumentModel doc) {
        doc.setUpdatedAt(Instant.now());
        return documentRepository.save(doc);
    }

    public DocumentModel setError(String id, DocumentStatus failedStatus, String stage, String errorMessage) {
        DocumentModel doc = documentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Document not found: " + id));
        DocumentStatus oldStatus = doc.getStatus();
        doc.setStatus(failedStatus);
        doc.setLastError(errorMessage);
        doc.setLastErrorStage(stage);
        doc.setFailedAt(Instant.now());
        doc.setUpdatedAt(Instant.now());
        DocumentModel saved = documentRepository.save(doc);
        audit(id, "PROCESSING_FAILED", "SYSTEM", "SYSTEM", Map.of(
                "from", oldStatus.name(), "to", failedStatus.name(),
                "stage", stage, "error", errorMessage != null ? errorMessage : "unknown"));
        statusNotifier.notifyStatusChange(id, failedStatus.name(), saved.getOriginalFileName());
        return saved;
    }

    public DocumentModel clearErrorForReprocess(String id) {
        DocumentModel doc = documentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Document not found: " + id));
        doc.setLastError(null);
        doc.setLastErrorStage(null);
        doc.setFailedAt(null);
        doc.setCancelledAt(null); // Clear cancel flag so reprocessing works
        // Reset pipeline timing so the UI shows fresh run timers
        doc.setProcessedAt(null);
        doc.setClassifiedAt(null);
        doc.setGovernanceAppliedAt(null);
        doc.setPiiScannedAt(null);
        doc.setRetryCount(doc.getRetryCount() + 1);
        doc.setStatus(DocumentStatus.UPLOADED);
        doc.setUpdatedAt(Instant.now());
        return documentRepository.save(doc);
    }

    public DocumentModel deleteDocument(String id, String performedBy) {
        DocumentModel doc = documentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Document not found: " + id));

        // Delete from object storage (tolerate missing file — may already be gone)
        if (doc.getStorageKey() != null) {
            try {
                String bucket = doc.getStorageBucket() != null ? doc.getStorageBucket() : objectStorage.getDefaultBucket();
                objectStorage.delete(bucket, doc.getStorageKey());
            } catch (Exception e) {
                log.warn("Could not delete storage object for document {}: {}", id, e.getMessage());
            }
        }

        documentRepository.deleteById(id);
        audit(id, "DOCUMENT_DELETED", performedBy, "USER", Map.of(
                "fileName", doc.getOriginalFileName() != null ? doc.getOriginalFileName() : "unknown",
                "status", doc.getStatus() != null ? doc.getStatus().name() : "unknown"));
        return doc;
    }

    public DocumentModel getBySlug(String slug) {
        return documentRepository.findBySlug(slug).orElse(null);
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

    public List<DocumentModel> getByCategoryAndUploader(String categoryId, String categoryName, String uploadedBy) {
        List<DocumentModel> byId = documentRepository.findByCategoryIdAndUploadedByOrderByOriginalFileNameAsc(categoryId, uploadedBy);
        if (!byId.isEmpty()) return byId;
        // Fallback: LLM may have saved category name instead of ID
        return documentRepository.findByCategoryNameAndUploadedByOrderByOriginalFileNameAsc(categoryName, uploadedBy);
    }

    public List<DocumentModel> getUnclassifiedByUploader(String uploadedBy) {
        return documentRepository.findByCategoryIdIsNullAndUploadedByOrderByCreatedAtDesc(uploadedBy);
    }

    public Page<DocumentModel> search(String uploadedBy, String q, String status,
                                       String sensitivity, String category, String classificationCode,
                                       String mimeType, Pageable pageable) {
        List<Criteria> filters = new ArrayList<>();
        filters.add(Criteria.where("uploadedBy").is(uploadedBy));

        if (q != null && !q.isBlank()) {
            String pattern = ".*" + java.util.regex.Pattern.quote(q.trim()) + ".*";
            filters.add(new Criteria().orOperator(
                    Criteria.where("originalFileName").regex(pattern, "i"),
                    Criteria.where("categoryName").regex(pattern, "i"),
                    Criteria.where("classificationCode").regex(pattern, "i"),
                    Criteria.where("tags").regex(pattern, "i")
            ));
        }
        if (status != null && !status.isBlank()) {
            filters.add(Criteria.where("status").is(DocumentStatus.valueOf(status)));
        }
        if (sensitivity != null && !sensitivity.isBlank()) {
            filters.add(Criteria.where("sensitivityLabel").is(SensitivityLabel.valueOf(sensitivity)));
        }
        if (category != null && !category.isBlank()) {
            filters.add(Criteria.where("categoryName").is(category));
        }
        if (classificationCode != null && !classificationCode.isBlank()) {
            // Prefix match: "FIN-AP" matches "FIN-AP-PAY", "FIN-AP-BIL", etc.
            filters.add(Criteria.where("classificationCode").regex(
                    "^" + java.util.regex.Pattern.quote(classificationCode), "i"));
        }
        if (mimeType != null && !mimeType.isBlank()) {
            filters.add(Criteria.where("mimeType").regex(".*" + java.util.regex.Pattern.quote(mimeType) + ".*", "i"));
        }

        Query query = new Query(new Criteria().andOperator(filters.toArray(new Criteria[0])))
                .with(pageable);

        List<DocumentModel> results = mongoTemplate.find(query, DocumentModel.class);
        return PageableExecutionUtils.getPage(results, pageable,
                () -> mongoTemplate.count(Query.of(query).limit(-1).skip(-1), DocumentModel.class));
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
