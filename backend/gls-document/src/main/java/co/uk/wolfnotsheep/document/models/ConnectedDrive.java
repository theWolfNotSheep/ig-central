package co.uk.wolfnotsheep.document.models;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Represents a connected storage drive — local storage, Google Drive, S3 bucket,
 * SharePoint site, Box folder, or network share. All document storage goes through
 * a connected drive.
 */
@Document(collection = "connected_drives")
public class ConnectedDrive {

    @Id
    private String id;

    @Indexed
    private String userId; // null for system drives (available to all users)

    // ── Provider identity ─────────────────────────────────
    private String provider;               // Legacy string: "LOCAL", "GOOGLE_DRIVE", etc.
    private StorageProviderType providerType; // Enum version (preferred)
    private String displayName;            // User-facing: "My Google Drive", "Project Archive (S3)"
    private String providerAccountEmail;   // For OAuth providers
    private String providerAccountName;

    // ── Provider-specific config ──────────────────────────
    /** Provider-specific connection config: S3 bucket/region, SMB host/share, etc. */
    private Map<String, String> config;

    // ── OAuth tokens (for Google Drive, SharePoint, Box) ──
    private String accessToken;
    private String refreshToken;
    private Instant tokenExpiresAt;
    private String grantedScopes;

    // ── Drive settings ────────────────────────────────────
    private List<String> monitoredFolderIds;
    private boolean systemDrive;  // true for auto-created Local Storage drive
    private boolean active;
    private Instant connectedAt;
    private Instant lastSyncAt;

    public ConnectedDrive() {}

    /** Check if this is a writable drive. For OAuth providers, checks scopes. For local/S3, always true. */
    public boolean hasWriteAccess() {
        if (providerType == StorageProviderType.LOCAL || providerType == StorageProviderType.S3 || providerType == StorageProviderType.SMB) {
            return true; // Local and direct-access providers are always writable
        }
        // OAuth providers: check granted scopes
        return grantedScopes != null && grantedScopes.contains("https://www.googleapis.com/auth/drive")
                && !grantedScopes.contains("drive.readonly");
    }

    /** Check if this drive needs reconnection (has scopes but they're insufficient). */
    public boolean needsReconnect() {
        return grantedScopes != null && !hasWriteAccess();
    }

    // ── Getters & setters ─────────────────────────────────

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public StorageProviderType getProviderType() { return providerType; }
    public void setProviderType(StorageProviderType providerType) { this.providerType = providerType; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getProviderAccountEmail() { return providerAccountEmail; }
    public void setProviderAccountEmail(String providerAccountEmail) { this.providerAccountEmail = providerAccountEmail; }

    public String getProviderAccountName() { return providerAccountName; }
    public void setProviderAccountName(String providerAccountName) { this.providerAccountName = providerAccountName; }

    public Map<String, String> getConfig() { return config; }
    public void setConfig(Map<String, String> config) { this.config = config; }

    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }

    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }

    public Instant getTokenExpiresAt() { return tokenExpiresAt; }
    public void setTokenExpiresAt(Instant tokenExpiresAt) { this.tokenExpiresAt = tokenExpiresAt; }

    public String getGrantedScopes() { return grantedScopes; }
    public void setGrantedScopes(String grantedScopes) { this.grantedScopes = grantedScopes; }

    public List<String> getMonitoredFolderIds() { return monitoredFolderIds; }
    public void setMonitoredFolderIds(List<String> monitoredFolderIds) { this.monitoredFolderIds = monitoredFolderIds; }

    public boolean isSystemDrive() { return systemDrive; }
    public void setSystemDrive(boolean systemDrive) { this.systemDrive = systemDrive; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public Instant getConnectedAt() { return connectedAt; }
    public void setConnectedAt(Instant connectedAt) { this.connectedAt = connectedAt; }

    public Instant getLastSyncAt() { return lastSyncAt; }
    public void setLastSyncAt(Instant lastSyncAt) { this.lastSyncAt = lastSyncAt; }
}
