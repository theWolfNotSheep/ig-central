package co.uk.wolfnotsheep.governance.models;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

/**
 * Records a human correction to an LLM classification.
 * These corrections are fed back to the LLM via MCP tools
 * to improve future classification accuracy.
 */
@Document(collection = "classification_corrections")
public class ClassificationCorrection {

    @Id
    private String id;

    @Indexed
    private String documentId;

    // What the LLM originally decided
    private String originalCategoryId;
    private String originalCategoryName;
    private SensitivityLabel originalSensitivity;
    private double originalConfidence;

    // What the human corrected it to
    private String correctedCategoryId;
    private String correctedCategoryName;
    private SensitivityLabel correctedSensitivity;

    // What changed
    private CorrectionType correctionType;
    private String reason;

    // Document characteristics (for similarity matching)
    private String mimeType;
    private List<String> keywords;

    // PII corrections
    private List<PiiCorrection> piiCorrections;

    // Audit
    @Indexed
    private String correctedBy;
    private Instant correctedAt;

    public ClassificationCorrection() {}

    public enum CorrectionType {
        CATEGORY_CHANGED,
        SENSITIVITY_CHANGED,
        BOTH_CHANGED,
        PII_FLAGGED,
        PII_DISMISSED,
        APPROVED_CORRECT
    }

    public record PiiCorrection(
            String type,
            String description,
            String context
    ) {}

    // ── Getters & Setters ────────────────────────────────

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getDocumentId() { return documentId; }
    public void setDocumentId(String documentId) { this.documentId = documentId; }

    public String getOriginalCategoryId() { return originalCategoryId; }
    public void setOriginalCategoryId(String originalCategoryId) { this.originalCategoryId = originalCategoryId; }

    public String getOriginalCategoryName() { return originalCategoryName; }
    public void setOriginalCategoryName(String originalCategoryName) { this.originalCategoryName = originalCategoryName; }

    public SensitivityLabel getOriginalSensitivity() { return originalSensitivity; }
    public void setOriginalSensitivity(SensitivityLabel originalSensitivity) { this.originalSensitivity = originalSensitivity; }

    public double getOriginalConfidence() { return originalConfidence; }
    public void setOriginalConfidence(double originalConfidence) { this.originalConfidence = originalConfidence; }

    public String getCorrectedCategoryId() { return correctedCategoryId; }
    public void setCorrectedCategoryId(String correctedCategoryId) { this.correctedCategoryId = correctedCategoryId; }

    public String getCorrectedCategoryName() { return correctedCategoryName; }
    public void setCorrectedCategoryName(String correctedCategoryName) { this.correctedCategoryName = correctedCategoryName; }

    public SensitivityLabel getCorrectedSensitivity() { return correctedSensitivity; }
    public void setCorrectedSensitivity(SensitivityLabel correctedSensitivity) { this.correctedSensitivity = correctedSensitivity; }

    public CorrectionType getCorrectionType() { return correctionType; }
    public void setCorrectionType(CorrectionType correctionType) { this.correctionType = correctionType; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }

    public List<String> getKeywords() { return keywords; }
    public void setKeywords(List<String> keywords) { this.keywords = keywords; }

    public List<PiiCorrection> getPiiCorrections() { return piiCorrections; }
    public void setPiiCorrections(List<PiiCorrection> piiCorrections) { this.piiCorrections = piiCorrections; }

    public String getCorrectedBy() { return correctedBy; }
    public void setCorrectedBy(String correctedBy) { this.correctedBy = correctedBy; }

    public Instant getCorrectedAt() { return correctedAt; }
    public void setCorrectedAt(Instant correctedAt) { this.correctedAt = correctedAt; }
}
