package co.uk.wolfnotsheep.governance.models;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * User feedback on a pipeline block's output.
 * Corrections, false positives, missed detections, and suggestions
 * are all tracked here to improve block accuracy over time.
 */
@Document(collection = "block_feedback")
public class BlockFeedback {

    @Id
    private String id;

    @Indexed
    private String blockId;

    private int blockVersion;
    private String documentId;
    private String userId;
    private String userEmail;

    private FeedbackType type;
    private String details;
    private String suggestion;

    private String originalValue;
    private String correctedValue;

    private Instant timestamp;

    public BlockFeedback() {
        this.timestamp = Instant.now();
    }

    public enum FeedbackType {
        CORRECTION,
        FALSE_POSITIVE,
        MISSED,
        SUGGESTION
    }

    // Getters & setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getBlockId() { return blockId; }
    public void setBlockId(String blockId) { this.blockId = blockId; }

    public int getBlockVersion() { return blockVersion; }
    public void setBlockVersion(int blockVersion) { this.blockVersion = blockVersion; }

    public String getDocumentId() { return documentId; }
    public void setDocumentId(String documentId) { this.documentId = documentId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getUserEmail() { return userEmail; }
    public void setUserEmail(String userEmail) { this.userEmail = userEmail; }

    public FeedbackType getType() { return type; }
    public void setType(FeedbackType type) { this.type = type; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }

    public String getSuggestion() { return suggestion; }
    public void setSuggestion(String suggestion) { this.suggestion = suggestion; }

    public String getOriginalValue() { return originalValue; }
    public void setOriginalValue(String originalValue) { this.originalValue = originalValue; }

    public String getCorrectedValue() { return correctedValue; }
    public void setCorrectedValue(String correctedValue) { this.correctedValue = correctedValue; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
}
