package co.uk.wolfnotsheep.governance.models;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

/**
 * Stores a structural fingerprint of a document template.
 * Used by the templateFingerprint accelerator to skip LLM classification
 * when a document matches a known template structure.
 */
@Document(collection = "template_fingerprints")
public class TemplateFingerprint {

    @Id
    private String id;

    @Indexed
    private String fingerprint;

    private String categoryId;
    private String categoryName;
    private SensitivityLabel sensitivityLabel;
    private List<String> tags;
    private String retentionScheduleId;

    private double confidence;
    private long matchCount;

    private String learnedFromDocumentId;
    private String mimeType;

    private Instant createdAt;
    private Instant lastMatchedAt;

    public TemplateFingerprint() {}

    // Getters & setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getFingerprint() { return fingerprint; }
    public void setFingerprint(String fingerprint) { this.fingerprint = fingerprint; }

    public String getCategoryId() { return categoryId; }
    public void setCategoryId(String categoryId) { this.categoryId = categoryId; }

    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }

    public SensitivityLabel getSensitivityLabel() { return sensitivityLabel; }
    public void setSensitivityLabel(SensitivityLabel sensitivityLabel) { this.sensitivityLabel = sensitivityLabel; }

    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }

    public String getRetentionScheduleId() { return retentionScheduleId; }
    public void setRetentionScheduleId(String retentionScheduleId) { this.retentionScheduleId = retentionScheduleId; }

    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }

    public long getMatchCount() { return matchCount; }
    public void setMatchCount(long matchCount) { this.matchCount = matchCount; }

    public String getLearnedFromDocumentId() { return learnedFromDocumentId; }
    public void setLearnedFromDocumentId(String learnedFromDocumentId) { this.learnedFromDocumentId = learnedFromDocumentId; }

    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getLastMatchedAt() { return lastMatchedAt; }
    public void setLastMatchedAt(Instant lastMatchedAt) { this.lastMatchedAt = lastMatchedAt; }
}
