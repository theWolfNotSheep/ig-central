package co.uk.wolfnotsheep.document.models;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

/**
 * Immutable audit trail entry. Append-only — never updated or deleted.
 * Records every significant action taken on a document.
 */
@Document(collection = "audit_events")
public class AuditEvent {

    @Id
    private String id;

    @Indexed
    private String documentId;

    @Indexed
    private String action;

    private String performedBy;
    private String performedByType; // USER, SYSTEM, LLM

    private Map<String, String> details;

    private Instant timestamp;

    public AuditEvent() {}

    public AuditEvent(String documentId, String action, String performedBy,
                      String performedByType, Map<String, String> details) {
        this.documentId = documentId;
        this.action = action;
        this.performedBy = performedBy;
        this.performedByType = performedByType;
        this.details = details;
        this.timestamp = Instant.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getDocumentId() { return documentId; }
    public void setDocumentId(String documentId) { this.documentId = documentId; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getPerformedBy() { return performedBy; }
    public void setPerformedBy(String performedBy) { this.performedBy = performedBy; }

    public String getPerformedByType() { return performedByType; }
    public void setPerformedByType(String performedByType) { this.performedByType = performedByType; }

    public Map<String, String> getDetails() { return details; }
    public void setDetails(Map<String, String> details) { this.details = details; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
}
