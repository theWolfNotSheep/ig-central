package co.uk.wolfnotsheep.governance.models;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Defines how long documents of a given classification must be retained,
 * and what happens when the retention period expires.
 *
 * Aligned with ISO 15489 retention and disposal schedule requirements.
 */
@Document(collection = "retention_schedules")
public class RetentionSchedule {

    @Id
    private String id;
    private String name;
    private String description;

    // Retention period — dual representation for backward compat + ISO 8601 interop
    private int retentionDays;              // Legacy: days (-1 = permanent)
    private String retentionDuration;       // ISO 8601: "P7Y", "P6M", "PERMANENT"

    // Retention trigger (ISO 15489)
    private String retentionTrigger;        // DATE_CREATED, DATE_CLOSED, EVENT_BASED, END_OF_FINANCIAL_YEAR, SUPERSEDED

    // Disposal
    private DispositionAction dispositionAction;

    // Legal hold
    private boolean legalHoldOverride;

    // Jurisdiction
    private String jurisdiction;            // "US", "UK", "EU"

    // Regulatory basis
    private String regulatoryBasis;

    // Disposal schedule reference (ISO 15489 audit trail)
    private String scheduleReference;       // "DS-2024-047"
    private String approvedBy;
    private Instant approvedDate;
    private Instant reviewDate;

    /** IDs of Legislation documents that mandate this retention period. */
    private List<String> legislationIds = new ArrayList<>();
    /** Free-text notes on specific sections/articles (e.g. "Section 386, Companies Act 2006"). */
    private String legislationNotes;

    // Import provenance
    private String sourcePackSlug;
    private Integer sourcePackVersion;
    private Instant importedAt;

    public RetentionSchedule() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public int getRetentionDays() { return retentionDays; }
    public void setRetentionDays(int retentionDays) { this.retentionDays = retentionDays; }

    public String getRetentionDuration() { return retentionDuration; }
    public void setRetentionDuration(String retentionDuration) { this.retentionDuration = retentionDuration; }

    public String getRetentionTrigger() { return retentionTrigger; }
    public void setRetentionTrigger(String retentionTrigger) { this.retentionTrigger = retentionTrigger; }

    public DispositionAction getDispositionAction() { return dispositionAction; }
    public void setDispositionAction(DispositionAction dispositionAction) { this.dispositionAction = dispositionAction; }

    public boolean isLegalHoldOverride() { return legalHoldOverride; }
    public void setLegalHoldOverride(boolean legalHoldOverride) { this.legalHoldOverride = legalHoldOverride; }

    public String getJurisdiction() { return jurisdiction; }
    public void setJurisdiction(String jurisdiction) { this.jurisdiction = jurisdiction; }

    public String getRegulatoryBasis() { return regulatoryBasis; }
    public void setRegulatoryBasis(String regulatoryBasis) { this.regulatoryBasis = regulatoryBasis; }

    public String getScheduleReference() { return scheduleReference; }
    public void setScheduleReference(String scheduleReference) { this.scheduleReference = scheduleReference; }

    public String getApprovedBy() { return approvedBy; }
    public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }

    public Instant getApprovedDate() { return approvedDate; }
    public void setApprovedDate(Instant approvedDate) { this.approvedDate = approvedDate; }

    public Instant getReviewDate() { return reviewDate; }
    public void setReviewDate(Instant reviewDate) { this.reviewDate = reviewDate; }

    public List<String> getLegislationIds() { return legislationIds; }
    public void setLegislationIds(List<String> legislationIds) { this.legislationIds = legislationIds; }

    public String getLegislationNotes() { return legislationNotes; }
    public void setLegislationNotes(String legislationNotes) { this.legislationNotes = legislationNotes; }

    public String getSourcePackSlug() { return sourcePackSlug; }
    public void setSourcePackSlug(String sourcePackSlug) { this.sourcePackSlug = sourcePackSlug; }

    public Integer getSourcePackVersion() { return sourcePackVersion; }
    public void setSourcePackVersion(Integer sourcePackVersion) { this.sourcePackVersion = sourcePackVersion; }

    public Instant getImportedAt() { return importedAt; }
    public void setImportedAt(Instant importedAt) { this.importedAt = importedAt; }

    public enum DispositionAction {
        DELETE,         // Securely destroy
        ARCHIVE,        // Move to archive storage
        TRANSFER,       // Transfer to external archive (e.g., national archive)
        REVIEW,         // Manual review before deciding
        ANONYMISE,      // Remove PII before archiving (GDPR)
        PERMANENT       // Never dispose — retain indefinitely
    }
}
