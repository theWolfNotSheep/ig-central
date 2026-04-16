package co.uk.wolfnotsheep.document.models;

import co.uk.wolfnotsheep.governance.models.ClassificationCategory.RetentionTrigger;
import co.uk.wolfnotsheep.governance.models.ClassificationCategory.TaxonomyLevel;
import co.uk.wolfnotsheep.governance.models.RetentionSchedule.DispositionAction;
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
    @Indexed
    private String storageProvider; // LOCAL, GOOGLE_DRIVE, S3, SHAREPOINT, BOX, SMB
    private String storageBucket;
    private String storageKey;
    private String storageTierId;
    @Indexed
    private String connectedDriveId; // FK to ConnectedDrive.id — which drive this document belongs to
    private Map<String, String> externalStorageRef; // provider-specific: fileId, driveId, webViewLink, ownerEmail

    // ── Processing state ─────────────────────────────────
    @Indexed
    private DocumentStatus status;
    private String extractedText;
    private String thumbnailKey;
    private int pageCount;

    // ── Classification (populated after LLM classification) ──
    private String classificationResultId;
    @Indexed
    private String categoryId;
    @Indexed
    private String categoryName;
    @Indexed
    private SensitivityLabel sensitivityLabel;
    private List<String> tags;
    private String summary; // LLM-generated document summary
    private Map<String, String> extractedMetadata;

    // ── ISO 15489 — denormalised from category at classification time ──
    @Indexed
    private String classificationCode;          // "COR-GOV-BRD" — stable hierarchical code
    private List<String> classificationPath;    // ["COR", "COR-GOV", "COR-GOV-BRD"]
    @Indexed
    private TaxonomyLevel classificationLevel;  // FUNCTION, ACTIVITY, TRANSACTION
    @Indexed
    private String jurisdiction;                // "US", "UK", "EU"
    private String legalCitation;               // "IRS requirements (IRC §6001)"
    @Indexed
    private boolean categoryPersonalData;       // record class typically contains personal data
    @Indexed
    private boolean vitalRecord;                // critical for business continuity
    private int taxonomyVersion;                // category version at the time of classification

    // ── Governance ───────────────────────────────────────
    private String retentionScheduleId;
    private Instant retentionExpiresAt;
    private RetentionTrigger retentionTrigger;          // DATE_CREATED, DATE_CLOSED, EVENT_BASED, etc.
    private String retentionPeriodText;                 // human-readable, e.g. "7 years after termination"
    private Instant retentionTriggerEventDate;          // null until trigger event happens (for non-DATE_CREATED triggers)
    @Indexed
    private RetentionStatus retentionStatus;            // AWAITING_TRIGGER, RUNNING, EXPIRED, DISPOSED, SUPERSEDED
    private DispositionAction expectedDispositionAction; // DELETE, ARCHIVE, TRANSFER, REVIEW, ANONYMISE, PERMANENT
    private boolean legalHold;
    private String legalHoldReason;
    private List<String> appliedPolicyIds;

    // ── Error tracking ─────────────────────────────────
    private String lastError;
    private String lastErrorStage;
    private Instant cancelledAt; // set when user cancels — consumers skip if set
    private Instant failedAt;
    private int retryCount;

    // ── Traits ─────────────────────────────────────────
    @Indexed
    private List<String> traits; // TEMPLATE, DRAFT, FINAL, SIGNED, INBOUND, OUTBOUND, INTERNAL, ORIGINAL, COPY, SCAN, GENERATED

    // ── PII Detection ──────────────────────────────────
    @Indexed
    private String piiStatus; // NONE, DETECTED, REVIEWED, REDACTED, EXEMPT
    private List<PiiEntity> piiFindings;
    private Instant piiScannedAt;

    // ── Dublin Core metadata (extracted from file) ──────
    private Map<String, String> dublinCore;

    // ── Slug ──────────────────────────────────────────
    @Indexed(unique = true, sparse = true)
    private String slug;

    // ── Pipeline ──────────────────────────────────────
    private String pipelineId;
    private String pipelineNodeId; // current position in the visual graph (null = not started or complete)

    // ── Folder ─────────────────────────────────────────
    @Indexed
    private String folderId;

    // ── Filing ─────────────────────────────────────────
    @Indexed
    private String filedToDriveId;
    private String filedToFolderId;
    private Instant filedAt;
    private String filedBy;

    // ── Ownership & audit ────────────────────────────────
    @Indexed
    private String uploadedBy;
    private String organisationId;
    @Indexed
    private Instant createdAt;
    @Indexed
    private Instant updatedAt;
    private Instant processedAt;
    @Indexed
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

    public String getStorageProvider() { return storageProvider; }
    public void setStorageProvider(String storageProvider) { this.storageProvider = storageProvider; }

    public String getStorageBucket() { return storageBucket; }
    public void setStorageBucket(String storageBucket) { this.storageBucket = storageBucket; }

    public Map<String, String> getExternalStorageRef() { return externalStorageRef; }
    public void setExternalStorageRef(Map<String, String> externalStorageRef) { this.externalStorageRef = externalStorageRef; }

    public String getStorageKey() { return storageKey; }
    public void setStorageKey(String storageKey) { this.storageKey = storageKey; }

    public String getStorageTierId() { return storageTierId; }
    public void setStorageTierId(String storageTierId) { this.storageTierId = storageTierId; }

    public String getConnectedDriveId() { return connectedDriveId; }
    public void setConnectedDriveId(String connectedDriveId) { this.connectedDriveId = connectedDriveId; }

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

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public Map<String, String> getExtractedMetadata() { return extractedMetadata; }
    public void setExtractedMetadata(Map<String, String> extractedMetadata) { this.extractedMetadata = extractedMetadata; }

    public String getClassificationCode() { return classificationCode; }
    public void setClassificationCode(String classificationCode) { this.classificationCode = classificationCode; }

    public List<String> getClassificationPath() { return classificationPath; }
    public void setClassificationPath(List<String> classificationPath) { this.classificationPath = classificationPath; }

    public TaxonomyLevel getClassificationLevel() { return classificationLevel; }
    public void setClassificationLevel(TaxonomyLevel classificationLevel) { this.classificationLevel = classificationLevel; }

    public String getJurisdiction() { return jurisdiction; }
    public void setJurisdiction(String jurisdiction) { this.jurisdiction = jurisdiction; }

    public String getLegalCitation() { return legalCitation; }
    public void setLegalCitation(String legalCitation) { this.legalCitation = legalCitation; }

    public boolean isCategoryPersonalData() { return categoryPersonalData; }
    public void setCategoryPersonalData(boolean categoryPersonalData) { this.categoryPersonalData = categoryPersonalData; }

    public boolean isVitalRecord() { return vitalRecord; }
    public void setVitalRecord(boolean vitalRecord) { this.vitalRecord = vitalRecord; }

    public int getTaxonomyVersion() { return taxonomyVersion; }
    public void setTaxonomyVersion(int taxonomyVersion) { this.taxonomyVersion = taxonomyVersion; }

    public String getRetentionScheduleId() { return retentionScheduleId; }
    public void setRetentionScheduleId(String retentionScheduleId) { this.retentionScheduleId = retentionScheduleId; }

    public Instant getRetentionExpiresAt() { return retentionExpiresAt; }
    public void setRetentionExpiresAt(Instant retentionExpiresAt) { this.retentionExpiresAt = retentionExpiresAt; }

    public RetentionTrigger getRetentionTrigger() { return retentionTrigger; }
    public void setRetentionTrigger(RetentionTrigger retentionTrigger) { this.retentionTrigger = retentionTrigger; }

    public String getRetentionPeriodText() { return retentionPeriodText; }
    public void setRetentionPeriodText(String retentionPeriodText) { this.retentionPeriodText = retentionPeriodText; }

    public Instant getRetentionTriggerEventDate() { return retentionTriggerEventDate; }
    public void setRetentionTriggerEventDate(Instant retentionTriggerEventDate) { this.retentionTriggerEventDate = retentionTriggerEventDate; }

    public RetentionStatus getRetentionStatus() { return retentionStatus; }
    public void setRetentionStatus(RetentionStatus retentionStatus) { this.retentionStatus = retentionStatus; }

    public DispositionAction getExpectedDispositionAction() { return expectedDispositionAction; }
    public void setExpectedDispositionAction(DispositionAction expectedDispositionAction) { this.expectedDispositionAction = expectedDispositionAction; }

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

    public List<String> getTraits() { return traits; }
    public void setTraits(List<String> traits) { this.traits = traits; }

    public String getPiiStatus() { return piiStatus; }
    public void setPiiStatus(String piiStatus) { this.piiStatus = piiStatus; }

    public List<PiiEntity> getPiiFindings() { return piiFindings; }
    public void setPiiFindings(List<PiiEntity> piiFindings) { this.piiFindings = piiFindings; }

    public Instant getPiiScannedAt() { return piiScannedAt; }
    public void setPiiScannedAt(Instant piiScannedAt) { this.piiScannedAt = piiScannedAt; }

    public Map<String, String> getDublinCore() { return dublinCore; }
    public void setDublinCore(Map<String, String> dublinCore) { this.dublinCore = dublinCore; }

    public String getPipelineId() { return pipelineId; }
    public void setPipelineId(String pipelineId) { this.pipelineId = pipelineId; }

    public String getPipelineNodeId() { return pipelineNodeId; }
    public void setPipelineNodeId(String pipelineNodeId) { this.pipelineNodeId = pipelineNodeId; }

    public String getFolderId() { return folderId; }
    public void setFolderId(String folderId) { this.folderId = folderId; }

    public String getFiledToDriveId() { return filedToDriveId; }
    public void setFiledToDriveId(String filedToDriveId) { this.filedToDriveId = filedToDriveId; }

    public String getFiledToFolderId() { return filedToFolderId; }
    public void setFiledToFolderId(String filedToFolderId) { this.filedToFolderId = filedToFolderId; }

    public Instant getFiledAt() { return filedAt; }
    public void setFiledAt(Instant filedAt) { this.filedAt = filedAt; }

    public String getFiledBy() { return filedBy; }
    public void setFiledBy(String filedBy) { this.filedBy = filedBy; }

    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }

    public String getLastErrorStage() { return lastErrorStage; }
    public void setLastErrorStage(String lastErrorStage) { this.lastErrorStage = lastErrorStage; }

    public Instant getFailedAt() { return failedAt; }
    public void setFailedAt(Instant failedAt) { this.failedAt = failedAt; }

    public int getRetryCount() { return retryCount; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }

    public Instant getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(Instant cancelledAt) { this.cancelledAt = cancelledAt; }

    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
}
