package co.uk.wolfnotsheep.governance.models;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Defines how long documents of a given classification must be retained,
 * and what happens when the retention period expires.
 */
@Document(collection = "retention_schedules")
public class RetentionSchedule {

    @Id
    private String id;
    private String name;
    private String description;
    private int retentionDays;
    private DispositionAction dispositionAction;
    private boolean legalHoldOverride;
    private String regulatoryBasis;

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

    public DispositionAction getDispositionAction() { return dispositionAction; }
    public void setDispositionAction(DispositionAction dispositionAction) { this.dispositionAction = dispositionAction; }

    public boolean isLegalHoldOverride() { return legalHoldOverride; }
    public void setLegalHoldOverride(boolean legalHoldOverride) { this.legalHoldOverride = legalHoldOverride; }

    public String getRegulatoryBasis() { return regulatoryBasis; }
    public void setRegulatoryBasis(String regulatoryBasis) { this.regulatoryBasis = regulatoryBasis; }

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
        DELETE,
        ARCHIVE,
        REVIEW,
        ANONYMISE
    }
}
