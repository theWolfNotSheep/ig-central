package co.uk.wolfnotsheep.hub.models;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "pack_download_records")
public class PackDownloadRecord {

    @Id
    private String id;

    private String packId;
    private int versionNumber;
    private String apiKeyPrefix;
    private String tenantName;
    private Instant downloadedAt;
    private List<String> componentsDownloaded = new ArrayList<>();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPackId() {
        return packId;
    }

    public void setPackId(String packId) {
        this.packId = packId;
    }

    public int getVersionNumber() {
        return versionNumber;
    }

    public void setVersionNumber(int versionNumber) {
        this.versionNumber = versionNumber;
    }

    public String getApiKeyPrefix() {
        return apiKeyPrefix;
    }

    public void setApiKeyPrefix(String apiKeyPrefix) {
        this.apiKeyPrefix = apiKeyPrefix;
    }

    public String getTenantName() {
        return tenantName;
    }

    public void setTenantName(String tenantName) {
        this.tenantName = tenantName;
    }

    public Instant getDownloadedAt() {
        return downloadedAt;
    }

    public void setDownloadedAt(Instant downloadedAt) {
        this.downloadedAt = downloadedAt;
    }

    public List<String> getComponentsDownloaded() {
        return componentsDownloaded;
    }

    public void setComponentsDownloaded(List<String> componentsDownloaded) {
        this.componentsDownloaded = componentsDownloaded;
    }
}
