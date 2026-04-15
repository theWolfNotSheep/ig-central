package co.uk.wolfnotsheep.governance.models;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * The output of LLM classification for a single document.
 * Stored as an immutable audit record — if re-classification occurs,
 * a new result is created (the previous one is not overwritten).
 */
@Document(collection = "classification_results")
public class DocumentClassificationResult {

    @Id
    private String id;
    private String documentId;

    /** The classification category the LLM assigned. */
    private String categoryId;
    private String categoryName;

    /** Sensitivity label the LLM determined. */
    private SensitivityLabel sensitivityLabel;

    /** Free-form tags extracted by the LLM (e.g. "invoice", "Q3-2025", "ACME Corp"). */
    private List<String> tags;

    /** Structured metadata the LLM extracted (e.g. {"vendor": "ACME", "amount": "15000"}). */
    private Map<String, String> extractedMetadata;

    /** IDs of governance policies the LLM determined apply to this document. */
    private List<String> applicablePolicyIds;

    /** The retention schedule assigned based on classification. */
    private String retentionScheduleId;

    /** LLM confidence score (0.0 to 1.0). Below threshold triggers human review. */
    private double confidence;

    /** The LLM's reasoning chain — why it made this classification. */
    private String reasoning;

    /** A brief human-readable summary of the document content. */
    private String summary;

    /** Which model was used for this classification. */
    private String modelId;

    private Instant classifiedAt;
    private boolean humanReviewed;
    private String reviewedBy;

    public DocumentClassificationResult() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getDocumentId() { return documentId; }
    public void setDocumentId(String documentId) { this.documentId = documentId; }

    public String getCategoryId() { return categoryId; }
    public void setCategoryId(String categoryId) { this.categoryId = categoryId; }

    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }

    public SensitivityLabel getSensitivityLabel() { return sensitivityLabel; }
    public void setSensitivityLabel(SensitivityLabel sensitivityLabel) { this.sensitivityLabel = sensitivityLabel; }

    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }

    public Map<String, String> getExtractedMetadata() { return extractedMetadata; }
    public void setExtractedMetadata(Map<String, String> extractedMetadata) { this.extractedMetadata = extractedMetadata; }

    public List<String> getApplicablePolicyIds() { return applicablePolicyIds; }
    public void setApplicablePolicyIds(List<String> applicablePolicyIds) { this.applicablePolicyIds = applicablePolicyIds; }

    public String getRetentionScheduleId() { return retentionScheduleId; }
    public void setRetentionScheduleId(String retentionScheduleId) { this.retentionScheduleId = retentionScheduleId; }

    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }

    public String getReasoning() { return reasoning; }
    public void setReasoning(String reasoning) { this.reasoning = reasoning; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public String getModelId() { return modelId; }
    public void setModelId(String modelId) { this.modelId = modelId; }

    public Instant getClassifiedAt() { return classifiedAt; }
    public void setClassifiedAt(Instant classifiedAt) { this.classifiedAt = classifiedAt; }

    public boolean isHumanReviewed() { return humanReviewed; }
    public void setHumanReviewed(boolean humanReviewed) { this.humanReviewed = humanReviewed; }

    public String getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(String reviewedBy) { this.reviewedBy = reviewedBy; }
}
