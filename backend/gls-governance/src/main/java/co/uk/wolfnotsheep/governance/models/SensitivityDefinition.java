package co.uk.wolfnotsheep.governance.models;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Admin-configurable definition of a sensitivity label.
 * The Java SensitivityLabel enum provides type safety in code,
 * but these definitions control what the LLM and UI display.
 */
@Document(collection = "sensitivity_definitions")
public class SensitivityDefinition {

    @Id
    private String id;

    @Indexed(unique = true)
    private String key;

    private String displayName;
    private String description;
    private int level;
    private String colour;
    private boolean active;

    private List<String> guidelines;
    private List<String> examples;

    /** IDs of Legislation documents that drive this sensitivity level. */
    private List<String> legislationIds = new ArrayList<>();

    // Import provenance
    private String sourcePackSlug;
    private Integer sourcePackVersion;
    private Instant importedAt;

    public SensitivityDefinition() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }

    public String getColour() { return colour; }
    public void setColour(String colour) { this.colour = colour; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public List<String> getGuidelines() { return guidelines; }
    public void setGuidelines(List<String> guidelines) { this.guidelines = guidelines; }

    public List<String> getExamples() { return examples; }
    public void setExamples(List<String> examples) { this.examples = examples; }

    public List<String> getLegislationIds() { return legislationIds; }
    public void setLegislationIds(List<String> legislationIds) { this.legislationIds = legislationIds; }

    public String getSourcePackSlug() { return sourcePackSlug; }
    public void setSourcePackSlug(String sourcePackSlug) { this.sourcePackSlug = sourcePackSlug; }

    public Integer getSourcePackVersion() { return sourcePackVersion; }
    public void setSourcePackVersion(Integer sourcePackVersion) { this.sourcePackVersion = sourcePackVersion; }

    public Instant getImportedAt() { return importedAt; }
    public void setImportedAt(Instant importedAt) { this.importedAt = importedAt; }
}
