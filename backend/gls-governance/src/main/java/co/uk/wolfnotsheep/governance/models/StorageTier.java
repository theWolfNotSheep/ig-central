package co.uk.wolfnotsheep.governance.models;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

/**
 * Represents a storage tier with specific capabilities.
 * The LLM uses these to decide where a document should be stored
 * based on its classification and sensitivity.
 */
@Document(collection = "storage_tiers")
public class StorageTier {

    @Id
    private String id;
    private String name;
    private String description;
    private String encryptionType;
    private boolean immutable;
    private boolean geographicallyRestricted;
    private String region;
    private List<SensitivityLabel> allowedSensitivities;
    private long maxFileSizeBytes;
    private double costPerGbMonth;

    public StorageTier() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getEncryptionType() { return encryptionType; }
    public void setEncryptionType(String encryptionType) { this.encryptionType = encryptionType; }

    public boolean isImmutable() { return immutable; }
    public void setImmutable(boolean immutable) { this.immutable = immutable; }

    public boolean isGeographicallyRestricted() { return geographicallyRestricted; }
    public void setGeographicallyRestricted(boolean geographicallyRestricted) { this.geographicallyRestricted = geographicallyRestricted; }

    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }

    public List<SensitivityLabel> getAllowedSensitivities() { return allowedSensitivities; }
    public void setAllowedSensitivities(List<SensitivityLabel> allowedSensitivities) { this.allowedSensitivities = allowedSensitivities; }

    public long getMaxFileSizeBytes() { return maxFileSizeBytes; }
    public void setMaxFileSizeBytes(long maxFileSizeBytes) { this.maxFileSizeBytes = maxFileSizeBytes; }

    public double getCostPerGbMonth() { return costPerGbMonth; }
    public void setCostPerGbMonth(double costPerGbMonth) { this.costPerGbMonth = costPerGbMonth; }
}
