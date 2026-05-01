package co.uk.wolfnotsheep.document.models;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Persistent record of system errors for admin visibility and resolution tracking.
 * Unlike logs, these persist in MongoDB and are visible in the admin monitoring UI.
 */
@Document(collection = "system_errors")
public class SystemError {

    @Id
    private String id;

    @Indexed
    private Instant timestamp;

    private String severity; // CRITICAL, ERROR, WARNING

    @Indexed
    private String category; // PIPELINE, STORAGE, QUEUE, AUTH, EXTERNAL_API, INTERNAL

    private String service;  // api, doc-processor, llm-worker, governance-enforcer
    private String message;
    private String stackTrace;
    private String documentId;
    private String userId;
    private String endpoint;
    private String httpMethod;

    @Indexed
    private boolean resolved;
    private String resolvedBy;
    private Instant resolvedAt;
    private String resolution;

    public SystemError() {
        this.timestamp = Instant.now();
        this.resolved = false;
    }

    public static SystemError of(String severity, String category, String message) {
        SystemError e = new SystemError();
        e.setSeverity(severity);
        e.setCategory(category);
        e.setMessage(message);
        e.setService("api");
        return e;
    }

    // Getters & setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getService() { return service; }
    public void setService(String service) { this.service = service; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getStackTrace() { return stackTrace; }
    public void setStackTrace(String stackTrace) { this.stackTrace = stackTrace; }

    public String getDocumentId() { return documentId; }
    public void setDocumentId(String documentId) { this.documentId = documentId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }

    public String getHttpMethod() { return httpMethod; }
    public void setHttpMethod(String httpMethod) { this.httpMethod = httpMethod; }

    public boolean isResolved() { return resolved; }
    public void setResolved(boolean resolved) { this.resolved = resolved; }

    public String getResolvedBy() { return resolvedBy; }
    public void setResolvedBy(String resolvedBy) { this.resolvedBy = resolvedBy; }

    public Instant getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(Instant resolvedAt) { this.resolvedAt = resolvedAt; }

    public String getResolution() { return resolution; }
    public void setResolution(String resolution) { this.resolution = resolution; }
}
