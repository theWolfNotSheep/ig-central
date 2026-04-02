package co.uk.wolfnotsheep.governance.models;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

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
    private boolean active;

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

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
