package co.uk.wolfnotsheep.platform.config.models;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

@Document("app_config")
public class AppConfig {

    @Id
    private String id;

    @Indexed(unique = true)
    private String key;

    private String category;

    private Object value;

    private String description;

    private Instant updatedAt;

    public AppConfig() {}

    // GETTERS
    public String getId() { return id; }
    public String getKey() { return key; }
    public String getCategory() { return category; }
    public Object getValue() { return value; }
    public String getDescription() { return description; }
    public Instant getUpdatedAt() { return updatedAt; }

    // SETTERS
    public void setId(String id) { this.id = id; }
    public void setKey(String key) { this.key = key; }
    public void setCategory(String category) { this.category = category; }
    public void setValue(Object value) { this.value = value; }
    public void setDescription(String description) { this.description = description; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
