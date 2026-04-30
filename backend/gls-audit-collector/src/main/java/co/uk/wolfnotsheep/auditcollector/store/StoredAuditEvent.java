package co.uk.wolfnotsheep.auditcollector.store;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;

import java.time.Instant;
import java.util.Map;

/**
 * Mongo-mapped audit event row. One document per envelope, partitioned
 * across two collections (Tier 1 + Tier 2) by the {@code @Document}
 * annotation on the subclasses {@link StoredTier1Event} /
 * {@link StoredTier2Event}.
 *
 * <p>The full envelope is preserved as-is on the {@code envelope} map;
 * the top-level fields are denormalised for indexed query (Tier 2
 * search filters, chain lookup keys).
 *
 * <p>Indexes per CSV #4 (chain integrity) and the contracted Tier 2
 * filter set (`documentId`, `eventType`, `actorService`, `from`/`to`).
 */
@CompoundIndexes({
        @CompoundIndex(name = "idx_resource_chain", def = "{'resourceType': 1, 'resourceId': 1, 'timestamp': 1}"),
        @CompoundIndex(name = "idx_tier2_search", def = "{'eventType': 1, 'timestamp': -1}")
})
public abstract class StoredAuditEvent {

    @Id
    protected String eventId;

    @Indexed
    protected String eventType;

    protected String tier;

    protected String schemaVersion;

    @Indexed
    protected Instant timestamp;

    @Indexed
    protected String documentId;

    protected String pipelineRunId;

    protected String nodeRunId;

    protected String traceparent;

    @Indexed
    protected String actorService;

    protected String actorType;

    protected String resourceType;

    protected String resourceId;

    protected String action;

    protected String outcome;

    protected String retentionClass;

    protected String previousEventHash;

    /**
     * Full envelope as a free-form map. Contract callers re-hydrate
     * this back into {@code AuditEvent} for response shape; the
     * collector itself reads only the denormalised fields above.
     */
    protected Map<String, Object> envelope;

    public StoredAuditEvent() { }

    public StoredAuditEvent(String eventId, String eventType, String tier, String schemaVersion,
                            Instant timestamp, String documentId, String pipelineRunId,
                            String nodeRunId, String traceparent, String actorService,
                            String actorType, String resourceType, String resourceId,
                            String action, String outcome, String retentionClass,
                            String previousEventHash, Map<String, Object> envelope) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.tier = tier;
        this.schemaVersion = schemaVersion;
        this.timestamp = timestamp;
        this.documentId = documentId;
        this.pipelineRunId = pipelineRunId;
        this.nodeRunId = nodeRunId;
        this.traceparent = traceparent;
        this.actorService = actorService;
        this.actorType = actorType;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.action = action;
        this.outcome = outcome;
        this.retentionClass = retentionClass;
        this.previousEventHash = previousEventHash;
        this.envelope = envelope;
    }

    public String getEventId() { return eventId; }
    public String getEventType() { return eventType; }
    public String getTier() { return tier; }
    public String getSchemaVersion() { return schemaVersion; }
    public Instant getTimestamp() { return timestamp; }
    public String getDocumentId() { return documentId; }
    public String getPipelineRunId() { return pipelineRunId; }
    public String getNodeRunId() { return nodeRunId; }
    public String getTraceparent() { return traceparent; }
    public String getActorService() { return actorService; }
    public String getActorType() { return actorType; }
    public String getResourceType() { return resourceType; }
    public String getResourceId() { return resourceId; }
    public String getAction() { return action; }
    public String getOutcome() { return outcome; }
    public String getRetentionClass() { return retentionClass; }
    public String getPreviousEventHash() { return previousEventHash; }
    public Map<String, Object> getEnvelope() { return envelope; }
}
