package co.uk.wolfnotsheep.hub.models;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * Hub admin user. Authenticates via HTTP Basic Auth against /api/hub/admin/**.
 * Roles control what the user can do (currently only HUB_ADMIN exists).
 */
@Document(collection = "hub_users")
public class HubUser {

    @Id
    private String id;

    @Indexed(unique = true)
    private String username;

    /** BCrypt-encoded password hash. */
    private String passwordHash;

    private String displayName;
    private String email;

    /** Role keys (e.g. HUB_ADMIN). Multiple roles supported for future use. */
    private Set<String> roles = new HashSet<>();

    private boolean active = true;
    private Instant createdAt;
    private Instant lastLoginAt;
    private String createdBy;

    public HubUser() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public Set<String> getRoles() { return roles; }
    public void setRoles(Set<String> roles) { this.roles = roles; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(Instant lastLoginAt) { this.lastLoginAt = lastLoginAt; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
}
