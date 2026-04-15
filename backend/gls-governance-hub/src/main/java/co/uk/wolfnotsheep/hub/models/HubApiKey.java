package co.uk.wolfnotsheep.hub.models;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "hub_api_keys")
public class HubApiKey {

    @Id
    private String id;

    @Indexed(unique = true)
    private String keyHash;

    private String keyPrefix;
    private String tenantName;
    private String tenantEmail;
    private List<String> permissions = new ArrayList<>();
    private boolean active;
    private int rateLimit;
    private int downloadQuota;
    private int downloadsThisMonth;
    private Instant quotaResetAt;
    private Instant createdAt;
    private Instant lastUsedAt;
    private Instant expiresAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getKeyHash() {
        return keyHash;
    }

    public void setKeyHash(String keyHash) {
        this.keyHash = keyHash;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    public String getTenantName() {
        return tenantName;
    }

    public void setTenantName(String tenantName) {
        this.tenantName = tenantName;
    }

    public String getTenantEmail() {
        return tenantEmail;
    }

    public void setTenantEmail(String tenantEmail) {
        this.tenantEmail = tenantEmail;
    }

    public List<String> getPermissions() {
        return permissions;
    }

    public void setPermissions(List<String> permissions) {
        this.permissions = permissions;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public int getRateLimit() {
        return rateLimit;
    }

    public void setRateLimit(int rateLimit) {
        this.rateLimit = rateLimit;
    }

    public int getDownloadQuota() {
        return downloadQuota;
    }

    public void setDownloadQuota(int downloadQuota) {
        this.downloadQuota = downloadQuota;
    }

    public int getDownloadsThisMonth() {
        return downloadsThisMonth;
    }

    public void setDownloadsThisMonth(int downloadsThisMonth) {
        this.downloadsThisMonth = downloadsThisMonth;
    }

    public Instant getQuotaResetAt() {
        return quotaResetAt;
    }

    public void setQuotaResetAt(Instant quotaResetAt) {
        this.quotaResetAt = quotaResetAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getLastUsedAt() {
        return lastUsedAt;
    }

    public void setLastUsedAt(Instant lastUsedAt) {
        this.lastUsedAt = lastUsedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }
}
