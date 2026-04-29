package co.uk.wolfnotsheep.bert.audit;

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
 * Tier 2 audit factory. {@code action="INFER"} groups everything
 * the BERT-tier emits; distinct from the router's
 * {@code action="CLASSIFY"} so audit consumers can filter the
 * cascade-internal tier calls separately from the cascade outcome.
 */
public final class BertEvents {

    public static final String EVENT_TYPE_COMPLETED = "INFER_COMPLETED";
    public static final String EVENT_TYPE_FAILED = "INFER_FAILED";

    private BertEvents() {
    }

    public static AuditEvent completed(
            String serviceName, String serviceVersion, String instanceId,
            String nodeRunId, String traceparent,
            String blockId, Integer blockVersion, String modelVersion,
            String label, Float confidence,
            long byteCount, long durationMs) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (nodeRunId != null) metadata.put("nodeRunId", nodeRunId);
        if (blockId != null) metadata.put("blockId", blockId);
        if (blockVersion != null) metadata.put("blockVersion", blockVersion);
        if (modelVersion != null) metadata.put("modelVersion", modelVersion);
        if (label != null) metadata.put("label", label);
        if (confidence != null) metadata.put("confidence", confidence);
        metadata.put("byteCount", byteCount);
        metadata.put("durationMs", durationMs);

        return new AuditEvent(
                eventId(), EVENT_TYPE_COMPLETED, Tier.SYSTEM,
                AuditEvent.CURRENT_SCHEMA_VERSION, Instant.now(),
                null, null, nodeRunId, traceparent,
                Actor.system(serviceName, serviceVersion, instanceId),
                null, "INFER", Outcome.SUCCESS,
                AuditDetails.metadataOnly(metadata),
                null, null);
    }

    public static AuditEvent failed(
            String serviceName, String serviceVersion, String instanceId,
            String nodeRunId, String traceparent,
            String errorCode, String errorMessage) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (nodeRunId != null) metadata.put("nodeRunId", nodeRunId);
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
                null, "INFER", Outcome.FAILURE, details,
                null, null);
    }

    private static String eventId() {
        return UUID.randomUUID().toString().replace("-", "").toUpperCase().substring(0, 26);
    }
}
