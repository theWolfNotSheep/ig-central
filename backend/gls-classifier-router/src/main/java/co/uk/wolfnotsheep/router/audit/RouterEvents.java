package co.uk.wolfnotsheep.router.audit;

import co.uk.wolfnotsheep.platformaudit.envelope.Actor;
import co.uk.wolfnotsheep.platformaudit.envelope.AuditDetails;
import co.uk.wolfnotsheep.platformaudit.envelope.AuditEvent;
import co.uk.wolfnotsheep.platformaudit.envelope.Outcome;
import co.uk.wolfnotsheep.platformaudit.envelope.Tier;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tier 2 audit-event factory. Distinct event-types from the
 * extraction family — readers want to filter classify calls
 * separately. {@code action="CLASSIFY"} groups everything the router
 * emits.
 */
public final class RouterEvents {

    public static final String EVENT_TYPE_COMPLETED = "CLASSIFY_COMPLETED";
    public static final String EVENT_TYPE_FAILED = "CLASSIFY_FAILED";

    private RouterEvents() {
    }

    public static AuditEvent completed(
            String serviceName, String serviceVersion, String instanceId,
            String nodeRunId, String traceparent,
            String blockId, Integer blockVersion,
            String tierOfDecision, Float confidence,
            long byteCount, long durationMs) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("nodeRunId", nodeRunId);
        if (blockId != null) metadata.put("blockId", blockId);
        if (blockVersion != null) metadata.put("blockVersion", blockVersion);
        if (tierOfDecision != null) metadata.put("tierOfDecision", tierOfDecision);
        if (confidence != null) metadata.put("confidence", confidence);
        metadata.put("byteCount", byteCount);
        metadata.put("durationMs", durationMs);

        return new AuditEvent(
                eventId(), EVENT_TYPE_COMPLETED, Tier.SYSTEM,
                AuditEvent.CURRENT_SCHEMA_VERSION, Instant.now(),
                null, null, nodeRunId, traceparent,
                Actor.system(serviceName, serviceVersion, instanceId),
                null, "CLASSIFY", Outcome.SUCCESS,
                AuditDetails.metadataOnly(metadata),
                null, null);
    }

    public static AuditEvent failed(
            String serviceName, String serviceVersion, String instanceId,
            String nodeRunId, String traceparent,
            String errorCode, String errorMessage) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("nodeRunId", nodeRunId);
        metadata.put("errorCode", errorCode);
        Map<String, Object> content = new LinkedHashMap<>();
        if (errorMessage != null) content.put("errorMessage", errorMessage);
        AuditDetails details = content.isEmpty()
                ? AuditDetails.metadataOnly(metadata)
                : AuditDetails.of(metadata, content);

        return new AuditEvent(
                eventId(), EVENT_TYPE_FAILED, Tier.SYSTEM,
                AuditEvent.CURRENT_SCHEMA_VERSION, Instant.now(),
                null, null, nodeRunId, traceparent,
                Actor.system(serviceName, serviceVersion, instanceId),
                null, "CLASSIFY", Outcome.FAILURE, details,
                null, null);
    }

    private static String eventId() {
        return UUID.randomUUID().toString().replace("-", "").toUpperCase().substring(0, 26);
    }
}
