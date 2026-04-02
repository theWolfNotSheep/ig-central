package co.uk.wolfnotsheep.platform.products.models;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("app_features")
public class Feature {

    @Id
    private String id;

    private String name;

    @Indexed(unique = true)
    private String permissionKey;

    private String description;

    private String category;

    private String group;

    private String status = "ACTIVE";

    public Feature() {}

    // GETTERS
    public String getId() { return id; }
    public String getName() { return name; }
    public String getPermissionKey() { return permissionKey; }
    public String getDescription() { return description; }
    public String getCategory() { return category; }
    public String getGroup() { return group; }
    public String getStatus() { return status; }

    // SETTERS
    public void setId(String id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setPermissionKey(String permissionKey) { this.permissionKey = permissionKey; }
    public void setDescription(String description) { this.description = description; }
    public void setCategory(String category) { this.category = category; }
    public void setGroup(String group) { this.group = group; }
    public void setStatus(String status) { this.status = status; }
}
