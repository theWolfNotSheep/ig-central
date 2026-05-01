package co.uk.wolfnotsheep.governance.models;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A node in the ISO 15489 Business Classification Scheme (BCS).
 * Hierarchy: Function > Activity > Transaction > Record
 * Example: HR (Function) > Employee Records (Activity) > Performance Reviews (Transaction)
 *
 * The LLM uses this taxonomy to classify incoming documents into
 * the correct category via zero-shot reasoning.
 */
@Document(collection = "classification_categories")
public class ClassificationCategory {

    public enum TaxonomyLevel {
        FUNCTION,       // Top-level business function (e.g., "Human Resources")
        ACTIVITY,       // Major task within a function (e.g., "Recruitment")
        TRANSACTION     // Specific action within an activity (e.g., "Job Application")
    }

    public enum NodeStatus {
        ACTIVE,
        INACTIVE,
        DEPRECATED
    }

    public enum RetentionTrigger {
        DATE_CREATED,
        DATE_LAST_MODIFIED,
        DATE_CLOSED,
        EVENT_BASED,
        END_OF_FINANCIAL_YEAR,
        SUPERSEDED
    }

    @Id
    private String id;

    // ISO 15489 BCS identification
    @Indexed(unique = true, sparse = true)
    private String classificationCode;      // "HR-EMP-PER" — unique hierarchical code
    private String name;
    private String description;
    private String scopeNotes;              // Inclusion/exclusion guidance for classifiers
    private TaxonomyLevel level;            // FUNCTION, ACTIVITY, TRANSACTION
    private String parentId;
    private List<String> path = new ArrayList<>();  // Materialised path of codes: ["HR", "HR-EMP", "HR-EMP-PER"]
    private int sortOrder;

    // Classification aids (used by LLM)
    private List<String> keywords;
    private SensitivityLabel defaultSensitivity;

    // Linked governance
    private String retentionScheduleId;
    private String metadataSchemaId;

    // ISO 15489 spreadsheet fields
    private String jurisdiction;                     // "US", "UK", "EU"
    private List<String> typicalRecords = new ArrayList<>();  // "Board agendas, minutes, resolutions"
    private String retentionPeriodText;              // "Permanent / 7+ years" — human-readable inline
    private String legalCitation;                    // "State corporate statutes; Sarbanes-Oxley Act"

    // Retention & disposal (node-level overrides — inherit from RetentionSchedule if null)
    private RetentionTrigger retentionTrigger;
    private String retentionTriggerDescription;     // Free-text for EVENT_BASED triggers

    // Ownership & review (ISO 15489 accountability)
    private String owner;                           // Business owner responsible for this area
    private String custodian;                       // Person/team managing day-to-day
    private String reviewCycleDuration;             // ISO 8601 duration: "P1Y" = review annually
    private Instant lastReviewedAt;
    private Instant nextReviewAt;

    // Flags
    private boolean personalDataFlag;               // Records here typically contain personal data
    private boolean vitalRecordFlag;                // Vital for business continuity

    // Status & version
    private NodeStatus status = NodeStatus.ACTIVE;
    private int version = 1;

    // Import provenance
    private String sourcePackSlug;
    private Integer sourcePackVersion;
    private Instant importedAt;

    public ClassificationCategory() {}

    // ── Getters & setters ─────────────────────────────────────────────

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getClassificationCode() { return classificationCode; }
    public void setClassificationCode(String classificationCode) { this.classificationCode = classificationCode; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getScopeNotes() { return scopeNotes; }
    public void setScopeNotes(String scopeNotes) { this.scopeNotes = scopeNotes; }

    public TaxonomyLevel getLevel() { return level; }
    public void setLevel(TaxonomyLevel level) { this.level = level; }

    public String getParentId() { return parentId; }
    public void setParentId(String parentId) { this.parentId = parentId; }

    public List<String> getPath() { return path; }
    public void setPath(List<String> path) { this.path = path; }

    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }

    public List<String> getKeywords() { return keywords; }
    public void setKeywords(List<String> keywords) { this.keywords = keywords; }

    public SensitivityLabel getDefaultSensitivity() { return defaultSensitivity; }
    public void setDefaultSensitivity(SensitivityLabel defaultSensitivity) { this.defaultSensitivity = defaultSensitivity; }

    public String getRetentionScheduleId() { return retentionScheduleId; }
    public void setRetentionScheduleId(String retentionScheduleId) { this.retentionScheduleId = retentionScheduleId; }

    public String getMetadataSchemaId() { return metadataSchemaId; }
    public void setMetadataSchemaId(String metadataSchemaId) { this.metadataSchemaId = metadataSchemaId; }

    public String getJurisdiction() { return jurisdiction; }
    public void setJurisdiction(String jurisdiction) { this.jurisdiction = jurisdiction; }

    public List<String> getTypicalRecords() { return typicalRecords; }
    public void setTypicalRecords(List<String> typicalRecords) { this.typicalRecords = typicalRecords; }

    public String getRetentionPeriodText() { return retentionPeriodText; }
    public void setRetentionPeriodText(String retentionPeriodText) { this.retentionPeriodText = retentionPeriodText; }

    public String getLegalCitation() { return legalCitation; }
    public void setLegalCitation(String legalCitation) { this.legalCitation = legalCitation; }

    /** @deprecated Use {@link #getLegalCitation()} */
    public String getDisposalAuthority() { return legalCitation; }
    /** @deprecated Use {@link #setLegalCitation(String)} */
    public void setDisposalAuthority(String disposalAuthority) { this.legalCitation = disposalAuthority; }

    public RetentionTrigger getRetentionTrigger() { return retentionTrigger; }
    public void setRetentionTrigger(RetentionTrigger retentionTrigger) { this.retentionTrigger = retentionTrigger; }

    public String getRetentionTriggerDescription() { return retentionTriggerDescription; }
    public void setRetentionTriggerDescription(String retentionTriggerDescription) { this.retentionTriggerDescription = retentionTriggerDescription; }

    public String getOwner() { return owner; }
    public void setOwner(String owner) { this.owner = owner; }

    public String getCustodian() { return custodian; }
    public void setCustodian(String custodian) { this.custodian = custodian; }

    public String getReviewCycleDuration() { return reviewCycleDuration; }
    public void setReviewCycleDuration(String reviewCycleDuration) { this.reviewCycleDuration = reviewCycleDuration; }

    public Instant getLastReviewedAt() { return lastReviewedAt; }
    public void setLastReviewedAt(Instant lastReviewedAt) { this.lastReviewedAt = lastReviewedAt; }

    public Instant getNextReviewAt() { return nextReviewAt; }
    public void setNextReviewAt(Instant nextReviewAt) { this.nextReviewAt = nextReviewAt; }

    public boolean isPersonalDataFlag() { return personalDataFlag; }
    public void setPersonalDataFlag(boolean personalDataFlag) { this.personalDataFlag = personalDataFlag; }

    public boolean isVitalRecordFlag() { return vitalRecordFlag; }
    public void setVitalRecordFlag(boolean vitalRecordFlag) { this.vitalRecordFlag = vitalRecordFlag; }

    public NodeStatus getStatus() { return status; }
    public void setStatus(NodeStatus status) { this.status = status; }

    /** Backward-compatible: returns true if status is ACTIVE */
    public boolean isActive() { return status == NodeStatus.ACTIVE; }
    public void setActive(boolean active) { this.status = active ? NodeStatus.ACTIVE : NodeStatus.INACTIVE; }

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }

    public String getSourcePackSlug() { return sourcePackSlug; }
    public void setSourcePackSlug(String sourcePackSlug) { this.sourcePackSlug = sourcePackSlug; }

    public Integer getSourcePackVersion() { return sourcePackVersion; }
    public void setSourcePackVersion(Integer sourcePackVersion) { this.sourcePackVersion = sourcePackVersion; }

    public Instant getImportedAt() { return importedAt; }
    public void setImportedAt(Instant importedAt) { this.importedAt = importedAt; }
}