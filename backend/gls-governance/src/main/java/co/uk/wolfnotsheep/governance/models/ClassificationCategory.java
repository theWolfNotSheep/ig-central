package co.uk.wolfnotsheep.governance.models;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

/**
 * A node in the classification taxonomy tree.
 * Example hierarchy: Legal > Contracts > Employment Contracts
 *
 * The LLM uses this taxonomy to classify incoming documents into
 * the correct category via zero-shot reasoning.
 */
@Document(collection = "classification_categories")
public class ClassificationCategory {

    @Id
    private String id;
    private String name;
    private String description;
    private String parentId;
    private List<String> keywords;
    private SensitivityLabel defaultSensitivity;
    private String retentionScheduleId;
    private String metadataSchemaId;
    private boolean active;

    // Import provenance
    private String sourcePackSlug;
    private Integer sourcePackVersion;
    private Instant importedAt;

    public ClassificationCategory() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getParentId() { return parentId; }
    public void setParentId(String parentId) { this.parentId = parentId; }

    public List<String> getKeywords() { return keywords; }
    public void setKeywords(List<String> keywords) { this.keywords = keywords; }

    public SensitivityLabel getDefaultSensitivity() { return defaultSensitivity; }
    public void setDefaultSensitivity(SensitivityLabel defaultSensitivity) { this.defaultSensitivity = defaultSensitivity; }

    public String getRetentionScheduleId() { return retentionScheduleId; }
    public void setRetentionScheduleId(String retentionScheduleId) { this.retentionScheduleId = retentionScheduleId; }

    public String getMetadataSchemaId() { return metadataSchemaId; }
    public void setMetadataSchemaId(String metadataSchemaId) { this.metadataSchemaId = metadataSchemaId; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public String getSourcePackSlug() { return sourcePackSlug; }
    public void setSourcePackSlug(String sourcePackSlug) { this.sourcePackSlug = sourcePackSlug; }

    public Integer getSourcePackVersion() { return sourcePackVersion; }
    public void setSourcePackVersion(Integer sourcePackVersion) { this.sourcePackVersion = sourcePackVersion; }

    public Instant getImportedAt() { return importedAt; }
    public void setImportedAt(Instant importedAt) { this.importedAt = importedAt; }
}
