package co.uk.wolfnotsheep.extraction.ocr.audit;

import co.uk.wolfnotsheep.platformaudit.envelope.Actor;
import co.uk.wolfnotsheep.platformaudit.envelope.AuditDetails;
import co.uk.wolfnotsheep.platformaudit.envelope.AuditEvent;
import co.uk.wolfnotsheep.platformaudit.envelope.Outcome;
import co.uk.wolfnotsheep.platformaudit.envelope.Tier;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tier 2 audit-event factory for the OCR service. Uses the same
 * {@code EXTRACTION_*} event types and {@code action="EXTRACT"} as
 * Tika + Archive so the extraction-family audit stream is
 * homogeneous; OCR-specific fields go in metadata
 * ({@code languages}, {@code pageCount}).
 */
public final class OcrEvents {

    public static final String EVENT_TYPE_COMPLETED = "EXTRACTION_COMPLETED";
    public static final String EVENT_TYPE_FAILED = "EXTRACTION_FAILED";

    private OcrEvents() {
    }

    public static AuditEvent completed(
            String serviceName, String serviceVersion, String instanceId,
            String nodeRunId, String traceparent,
            String detectedMimeType, List<String> languages,
            Integer pageCount, long byteCount, long durationMs) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("nodeRunId", nodeRunId);
        if (detectedMimeType != null) metadata.put("detectedMimeType", detectedMimeType);
        if (languages != null && !languages.isEmpty()) metadata.put("languages", languages);
        if (pageCount != null) metadata.put("pageCount", pageCount);
        metadata.put("byteCount", byteCount);
        metadata.put("durationMs", durationMs);

        return new AuditEvent(
                eventId(), EVENT_TYPE_COMPLETED, Tier.SYSTEM,
                AuditEvent.CURRENT_SCHEMA_VERSION, Instant.now(),
                null, null, nodeRunId, traceparent,
                Actor.system(serviceName, serviceVersion, instanceId),
                null, "EXTRACT", Outcome.SUCCESS,
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
                null, "EXTRACT", Outcome.FAILURE, details,
                null, null);
    }

    private static String eventId() {
        return UUID.randomUUID().toString().replace("-", "").toUpperCase().substring(0, 26);
    }
}
