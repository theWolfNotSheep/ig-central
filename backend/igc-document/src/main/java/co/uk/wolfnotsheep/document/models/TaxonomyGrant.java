package co.uk.wolfnotsheep.document.models;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Set;

/**
 * Grants a user access to documents in a specific taxonomy category.
 * With includeChildren=true, access extends to all descendant categories.
 */
@Document(collection = "taxonomy_grants")
public class TaxonomyGrant {

    @Id
    private String id;

    @Indexed
    private String userId;

    @Indexed
    private String categoryId;

    private boolean includeChildren;
    private Set<String> operations; // READ, CREATE, UPDATE, DELETE

    private String grantedBy;
    private Instant grantedAt;
    private Instant expiresAt;
    private String reason;

    public TaxonomyGrant() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getCategoryId() { return categoryId; }
    public void setCategoryId(String categoryId) { this.categoryId = categoryId; }

    public boolean isIncludeChildren() { return includeChildren; }
    public void setIncludeChildren(boolean includeChildren) { this.includeChildren = includeChildren; }

    public Set<String> getOperations() { return operations; }
    public void setOperations(Set<String> operations) { this.operations = operations; }

    public String getGrantedBy() { return grantedBy; }
    public void setGrantedBy(String grantedBy) { this.grantedBy = grantedBy; }

    public Instant getGrantedAt() { return grantedAt; }
    public void setGrantedAt(Instant grantedAt) { this.grantedAt = grantedAt; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
