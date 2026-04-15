package co.uk.wolfnotsheep.document.models;

/**
 * A single PII entity detected in a document.
 * Embedded in DocumentModel.piiFindings list.
 */
public class PiiEntity {

    private String type;
    private String matchedText;
    private String redactedText;
    private int offset;
    private double confidence;
    private DetectionMethod method;
    private boolean verified;
    private String verifiedBy;
    private boolean dismissed;
    private String dismissedBy;
    private String dismissalReason;

    public enum DetectionMethod {
        PATTERN,
        LLM
    }

    public PiiEntity() {}

    public PiiEntity(String type, String matchedText, String redactedText,
                     int offset, double confidence, DetectionMethod method) {
        this.type = type;
        this.matchedText = matchedText;
        this.redactedText = redactedText;
        this.offset = offset;
        this.confidence = confidence;
        this.method = method;
        this.verified = false;
    }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getMatchedText() { return matchedText; }
    public void setMatchedText(String matchedText) { this.matchedText = matchedText; }

    public String getRedactedText() { return redactedText; }
    public void setRedactedText(String redactedText) { this.redactedText = redactedText; }

    public int getOffset() { return offset; }
    public void setOffset(int offset) { this.offset = offset; }

    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }

    public DetectionMethod getMethod() { return method; }
    public void setMethod(DetectionMethod method) { this.method = method; }

    public boolean isVerified() { return verified; }
    public void setVerified(boolean verified) { this.verified = verified; }

    public String getVerifiedBy() { return verifiedBy; }
    public void setVerifiedBy(String verifiedBy) { this.verifiedBy = verifiedBy; }

    public boolean isDismissed() { return dismissed; }
    public void setDismissed(boolean dismissed) { this.dismissed = dismissed; }

    public String getDismissedBy() { return dismissedBy; }
    public void setDismissedBy(String dismissedBy) { this.dismissedBy = dismissedBy; }

    public String getDismissalReason() { return dismissalReason; }
    public void setDismissalReason(String dismissalReason) { this.dismissalReason = dismissalReason; }
}
