package co.uk.wolfnotsheep.auditcollector.store;

import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

@Document(collection = "audit_tier2_events")
public class StoredTier2Event extends StoredAuditEvent {

    public StoredTier2Event() { }

    public StoredTier2Event(String eventId, String eventType, String schemaVersion, Instant timestamp,
                            String documentId, String pipelineRunId, String nodeRunId, String traceparent,
                            String actorService, String actorType, String resourceType, String resourceId,
                            String action, String outcome, String retentionClass,
                            Map<String, Object> envelope) {
        super(eventId, eventType, "SYSTEM", schemaVersion, timestamp, documentId, pipelineRunId,
                nodeRunId, traceparent, actorService, actorType, resourceType, resourceId,
                action, outcome, retentionClass, /* previousEventHash */ null, envelope);
    }
}
