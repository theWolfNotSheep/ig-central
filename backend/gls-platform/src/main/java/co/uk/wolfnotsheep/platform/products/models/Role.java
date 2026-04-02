package co.uk.wolfnotsheep.platform.products.models;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;

@Document("app_roles")
public class Role {

    @Id
    private String id;

    private String name;

    @Indexed(unique = true)
    private String key;

    private String description;

    private String roleType;

    private List<String> featureIds = new ArrayList<>();

    private List<String> accountTypeScope = new ArrayList<>();

    private String status = "ACTIVE";

    private boolean systemProtected = false;

    public Role() {}

    // GETTERS
    public String getId() { return id; }
    public String getName() { return name; }
    public String getKey() { return key; }
    public String getDescription() { return description; }
    public String getRoleType() { return roleType; }
    public List<String> getFeatureIds() { return featureIds; }
    public List<String> getAccountTypeScope() { return accountTypeScope; }
    public String getStatus() { return status; }
    public boolean isSystemProtected() { return systemProtected; }

    // SETTERS
    public void setId(String id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setKey(String key) { this.key = key; }
    public void setDescription(String description) { this.description = description; }
    public void setRoleType(String roleType) { this.roleType = roleType; }
    public void setFeatureIds(List<String> featureIds) { this.featureIds = featureIds; }
    public void setAccountTypeScope(List<String> accountTypeScope) { this.accountTypeScope = accountTypeScope; }
    public void setStatus(String status) { this.status = status; }
    public void setSystemProtected(boolean systemProtected) { this.systemProtected = systemProtected; }
}
