package co.uk.wolfnotsheep.auditcollector.store;

import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

@Document(collection = "audit_tier1_events")
public class StoredTier1Event extends StoredAuditEvent {

    public StoredTier1Event() { }

    public StoredTier1Event(String eventId, String eventType, String schemaVersion, Instant timestamp,
                            String documentId, String pipelineRunId, String nodeRunId, String traceparent,
                            String actorService, String actorType, String resourceType, String resourceId,
                            String action, String outcome, String retentionClass, String previousEventHash,
                            Map<String, Object> envelope) {
        super(eventId, eventType, "DOMAIN", schemaVersion, timestamp, documentId, pipelineRunId,
                nodeRunId, traceparent, actorService, actorType, resourceType, resourceId,
                action, outcome, retentionClass, previousEventHash, envelope);
    }
}
