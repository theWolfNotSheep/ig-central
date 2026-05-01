package co.uk.wolfnotsheep.hub.models;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PackComponent {

    public enum ComponentType {
        TAXONOMY_CATEGORIES,
        RETENTION_SCHEDULES,
        SENSITIVITY_DEFINITIONS,
        GOVERNANCE_POLICIES,
        PII_TYPE_DEFINITIONS,
        METADATA_SCHEMAS,
        STORAGE_TIERS,
        TRAIT_DEFINITIONS,
        PIPELINE_BLOCKS,
        LEGISLATION
    }

    private ComponentType type;
    private String name;
    private String description;
    private int itemCount;
    private List<Map<String, Object>> data = new ArrayList<>();

    public ComponentType getType() {
        return type;
    }

    public void setType(ComponentType type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getItemCount() {
        return itemCount;
    }

    public void setItemCount(int itemCount) {
        this.itemCount = itemCount;
    }

    public List<Map<String, Object>> getData() {
        return data;
    }

    public void setData(List<Map<String, Object>> data) {
        this.data = data;
    }
}
