package co.uk.wolfnotsheep.governance.models;

/**
 * Sensitivity labels applied to documents after LLM classification.
 * These map directly to access control and encryption policies.
 */
public enum SensitivityLabel {

    PUBLIC("Public", "No restrictions — can be shared externally", 0),
    INTERNAL("Internal", "Organisation-internal only", 1),
    CONFIDENTIAL("Confidential", "Restricted to named roles/teams", 2),
    RESTRICTED("Restricted", "Highest sensitivity — legal, PII, regulatory", 3);

    private final String displayName;
    private final String description;
    private final int level;

    SensitivityLabel(String displayName, String description, int level) {
        this.displayName = displayName;
        this.description = description;
        this.level = level;
    }

    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public int getLevel() { return level; }
}
