package co.uk.wolfnotsheep.governance.models;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Defines a document trait — a characteristic that cuts across categories.
 * Traits are grouped by dimension (completeness, direction, provenance).
 * The LLM detects traits during classification.
 */
@Document(collection = "trait_definitions")
public class TraitDefinition {

    @Id
    private String id;

    @Indexed(unique = true)
    private String key; // TEMPLATE, DRAFT, FINAL, SIGNED, etc.

    private String displayName;
    private String description;
    private String dimension; // COMPLETENESS, DIRECTION, PROVENANCE

    private String detectionHint; // Instruction for the LLM on how to detect this trait
    private List<String> indicators; // Keywords/patterns that suggest this trait

    private boolean suppressPii; // If true, PII detection results are treated as informational only
    private boolean active;

    /**
     * Phase 1.7 / CSV #33. Categories this trait applies to. Empty
     * = global (current behaviour). Non-empty = scoped to those
     * category ids.
     */
    private List<String> applicableCategoryIds = new ArrayList<>();

    // Import provenance
    private String sourcePackSlug;
    private Integer sourcePackVersion;
    private Instant importedAt;

    public TraitDefinition() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getDimension() { return dimension; }
    public void setDimension(String dimension) { this.dimension = dimension; }

    public String getDetectionHint() { return detectionHint; }
    public void setDetectionHint(String detectionHint) { this.detectionHint = detectionHint; }

    public List<String> getIndicators() { return indicators; }
    public void setIndicators(List<String> indicators) { this.indicators = indicators; }

    public boolean isSuppressPii() { return suppressPii; }
    public void setSuppressPii(boolean suppressPii) { this.suppressPii = suppressPii; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public String getSourcePackSlug() { return sourcePackSlug; }
    public void setSourcePackSlug(String sourcePackSlug) { this.sourcePackSlug = sourcePackSlug; }

    public Integer getSourcePackVersion() { return sourcePackVersion; }
    public void setSourcePackVersion(Integer sourcePackVersion) { this.sourcePackVersion = sourcePackVersion; }

    public Instant getImportedAt() { return importedAt; }
    public void setImportedAt(Instant importedAt) { this.importedAt = importedAt; }

    public List<String> getApplicableCategoryIds() { return applicableCategoryIds; }
    public void setApplicableCategoryIds(List<String> applicableCategoryIds) {
        this.applicableCategoryIds = applicableCategoryIds == null ? new ArrayList<>() : applicableCategoryIds;
    }
}
