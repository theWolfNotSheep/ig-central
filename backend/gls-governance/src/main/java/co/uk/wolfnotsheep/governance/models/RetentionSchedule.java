package co.uk.wolfnotsheep.governance.models;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

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

    public enum DispositionAction {
        DELETE,
        ARCHIVE,
        REVIEW,
        ANONYMISE
    }
}
