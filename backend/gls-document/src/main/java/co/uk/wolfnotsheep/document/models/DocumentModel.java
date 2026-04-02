package co.uk.wolfnotsheep.document.models;

import co.uk.wolfnotsheep.governance.models.SensitivityLabel;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Core document record. Represents a single uploaded file and its
 * lifecycle through ingestion → processing → classification → governance.
 */
@Document(collection = "documents")
public class DocumentModel {

    @Id
    private String id;

    // ── File metadata ────────────────────────────────────
    private String fileName;
    private String originalFileName;
    private String mimeType;
    private long fileSizeBytes;
    private String sha256Hash;

    // ── Storage ──────────────────────────────────────────
    private String storageBucket;
    private String storageKey;
    private String storageTierId;

    // ── Processing state ─────────────────────────────────
    @Indexed
    private DocumentStatus status;
    private String extractedText;
    private String thumbnailKey;
    private int pageCount;

    // ── Classification (populated after LLM classification) ──
    private String classificationResultId;
    private String categoryId;
    private String categoryName;
    private SensitivityLabel sensitivityLabel;
    private List<String> tags;
    private Map<String, String> extractedMetadata;

    // ── Governance ───────────────────────────────────────
    private String retentionScheduleId;
    private Instant retentionExpiresAt;
    private boolean legalHold;
    private String legalHoldReason;
    private List<String> appliedPolicyIds;

    // ── Ownership & audit ────────────────────────────────
    @Indexed
    private String uploadedBy;
    private String organisationId;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant processedAt;
    private Instant classifiedAt;
    private Instant governanceAppliedAt;

    public DocumentModel() {}

    // ── Getters & Setters ────────────────────────────────

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getOriginalFileName() { return originalFileName; }
    public void setOriginalFileName(String originalFileName) { this.originalFileName = originalFileName; }

    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }

    public long getFileSizeBytes() { return fileSizeBytes; }
    public void setFileSizeBytes(long fileSizeBytes) { this.fileSizeBytes = fileSizeBytes; }

    public String getSha256Hash() { return sha256Hash; }
    public void setSha256Hash(String sha256Hash) { this.sha256Hash = sha256Hash; }

    public String getStorageBucket() { return storageBucket; }
    public void setStorageBucket(String storageBucket) { this.storageBucket = storageBucket; }

    public String getStorageKey() { return storageKey; }
    public void setStorageKey(String storageKey) { this.storageKey = storageKey; }

    public String getStorageTierId() { return storageTierId; }
    public void setStorageTierId(String storageTierId) { this.storageTierId = storageTierId; }

    public DocumentStatus getStatus() { return status; }
    public void setStatus(DocumentStatus status) { this.status = status; }

    public String getExtractedText() { return extractedText; }
    public void setExtractedText(String extractedText) { this.extractedText = extractedText; }

    public String getThumbnailKey() { return thumbnailKey; }
    public void setThumbnailKey(String thumbnailKey) { this.thumbnailKey = thumbnailKey; }

    public int getPageCount() { return pageCount; }
    public void setPageCount(int pageCount) { this.pageCount = pageCount; }

    public String getClassificationResultId() { return classificationResultId; }
    public void setClassificationResultId(String classificationResultId) { this.classificationResultId = classificationResultId; }

    public String getCategoryId() { return categoryId; }
    public void setCategoryId(String categoryId) { this.categoryId = categoryId; }

    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }

    public SensitivityLabel getSensitivityLabel() { return sensitivityLabel; }
    public void setSensitivityLabel(SensitivityLabel sensitivityLabel) { this.sensitivityLabel = sensitivityLabel; }

    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }

    public Map<String, String> getExtractedMetadata() { return extractedMetadata; }
    public void setExtractedMetadata(Map<String, String> extractedMetadata) { this.extractedMetadata = extractedMetadata; }

    public String getRetentionScheduleId() { return retentionScheduleId; }
    public void setRetentionScheduleId(String retentionScheduleId) { this.retentionScheduleId = retentionScheduleId; }

    public Instant getRetentionExpiresAt() { return retentionExpiresAt; }
    public void setRetentionExpiresAt(Instant retentionExpiresAt) { this.retentionExpiresAt = retentionExpiresAt; }

    public boolean isLegalHold() { return legalHold; }
    public void setLegalHold(boolean legalHold) { this.legalHold = legalHold; }

    public String getLegalHoldReason() { return legalHoldReason; }
    public void setLegalHoldReason(String legalHoldReason) { this.legalHoldReason = legalHoldReason; }

    public List<String> getAppliedPolicyIds() { return appliedPolicyIds; }
    public void setAppliedPolicyIds(List<String> appliedPolicyIds) { this.appliedPolicyIds = appliedPolicyIds; }

    public String getUploadedBy() { return uploadedBy; }
    public void setUploadedBy(String uploadedBy) { this.uploadedBy = uploadedBy; }

    public String getOrganisationId() { return organisationId; }
    public void setOrganisationId(String organisationId) { this.organisationId = organisationId; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public Instant getProcessedAt() { return processedAt; }
    public void setProcessedAt(Instant processedAt) { this.processedAt = processedAt; }

    public Instant getClassifiedAt() { return classifiedAt; }
    public void setClassifiedAt(Instant classifiedAt) { this.classifiedAt = classifiedAt; }

    public Instant getGovernanceAppliedAt() { return governanceAppliedAt; }
    public void setGovernanceAppliedAt(Instant governanceAppliedAt) { this.governanceAppliedAt = governanceAppliedAt; }
}
