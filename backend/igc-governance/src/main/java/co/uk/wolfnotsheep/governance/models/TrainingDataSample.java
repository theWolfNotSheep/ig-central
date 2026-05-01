package co.uk.wolfnotsheep.governance.models;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * A training data sample for the BERT classifier.
 * Stores extracted text paired with a category label.
 * Separate from governed documents — training data is not subject to retention or enforcement.
 */
@Document(collection = "bert_training_data")
public class TrainingDataSample {

    @Id
    private String id;

    private String text;                    // extracted text (truncated to max_text_length)

    @Indexed
    private String categoryId;              // taxonomy category ID
    private String categoryName;            // display name
    private String sensitivityLabel;        // e.g. "CONFIDENTIAL", "INTERNAL"

    @Indexed
    private String source;                  // MANUAL_UPLOAD, AUTO_COLLECTED, BULK_IMPORT

    @Indexed
    private String sourceDocumentId;        // if collected from a classified document

    private double confidence;              // classification confidence (if auto-collected)
    private boolean verified;               // human-reviewed this label
    private String fileName;                // original file name (for reference)

    private Instant createdAt;
    private Instant updatedAt;

    public TrainingDataSample() {}

    // ── Getters & Setters ────────────────────────────────

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public String getCategoryId() { return categoryId; }
    public void setCategoryId(String categoryId) { this.categoryId = categoryId; }

    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }

    public String getSensitivityLabel() { return sensitivityLabel; }
    public void setSensitivityLabel(String sensitivityLabel) { this.sensitivityLabel = sensitivityLabel; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getSourceDocumentId() { return sourceDocumentId; }
    public void setSourceDocumentId(String sourceDocumentId) { this.sourceDocumentId = sourceDocumentId; }

    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }

    public boolean isVerified() { return verified; }
    public void setVerified(boolean verified) { this.verified = verified; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
