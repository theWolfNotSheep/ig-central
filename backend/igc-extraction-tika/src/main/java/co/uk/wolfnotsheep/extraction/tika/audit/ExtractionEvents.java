package co.uk.wolfnotsheep.extraction.tika.audit;

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
 * Builds the Tier 2 audit envelopes the extraction service emits.
 * Pure factory — no Spring, no Mongo, no Rabbit. Lives separately from
 * the controller so the construction logic is unit-testable in
 * isolation and the controller stays focused on the HTTP plane.
 *
 * <p>Per CSV #5 / asyncapi `audit.tier2.{eventType}`: extraction
 * publishes only Tier 2 events ({@code SYSTEM} tier) — these are
 * operational records ({@code EXTRACTION_COMPLETED}, {@code EXTRACTION_FAILED}).
 * Document-anchored Tier 1 events (e.g. {@code DOCUMENT_INGESTED},
 * {@code DOCUMENT_CLASSIFIED}) are emitted by other services that own
 * the document lifecycle.
 *
 * <p>{@code eventId} is generated as 26 uppercase hex characters drawn
 * from a {@code UUID.randomUUID()}. Hex digits + A–F satisfy the
 * envelope schema's Crockford-base32 pattern (which only excludes
 * {@code I L O U}). Time-sortability is sacrificed compared to a real
 * ULID — flagged for the {@code igc-platform-audit} ULID utility
 * follow-up.
 */
public final class ExtractionEvents {

    public static final String EVENT_TYPE_COMPLETED = "EXTRACTION_COMPLETED";
    public static final String EVENT_TYPE_FAILED = "EXTRACTION_FAILED";

    private ExtractionEvents() {
    }

    public static AuditEvent completed(
            String serviceName,
            String serviceVersion,
            String instanceId,
            String nodeRunId,
            String traceparent,
            String detectedMimeType,
            Integer pageCount,
            long byteCount,
            long durationMs,
            boolean truncated) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("nodeRunId", nodeRunId);
        if (detectedMimeType != null) {
            metadata.put("detectedMimeType", detectedMimeType);
        }
        if (pageCount != null) {
            metadata.put("pageCount", pageCount);
        }
        metadata.put("byteCount", byteCount);
        metadata.put("durationMs", durationMs);
        metadata.put("truncated", truncated);

        return new AuditEvent(
                eventId(),
                EVENT_TYPE_COMPLETED,
                Tier.SYSTEM,
                AuditEvent.CURRENT_SCHEMA_VERSION,
                Instant.now(),
                /* documentId */ null,
                /* pipelineRunId */ null,
                nodeRunId,
                traceparent,
                Actor.system(serviceName, serviceVersion, instanceId),
                /* resource */ null,
                /* action */ "EXTRACT",
                Outcome.SUCCESS,
                AuditDetails.metadataOnly(metadata),
                /* retentionClass */ null,
                /* previousEventHash */ null);
    }

    public static AuditEvent failed(
            String serviceName,
            String serviceVersion,
            String instanceId,
            String nodeRunId,
            String traceparent,
            String errorCode,
            String errorMessage) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("nodeRunId", nodeRunId);
        metadata.put("errorCode", errorCode);
        Map<String, Object> content = new LinkedHashMap<>();
        if (errorMessage != null) {
            content.put("errorMessage", errorMessage);
        }
        AuditDetails details = content.isEmpty()
                ? AuditDetails.metadataOnly(metadata)
                : AuditDetails.of(metadata, content);

        return new AuditEvent(
                eventId(),
                EVENT_TYPE_FAILED,
                Tier.SYSTEM,
                AuditEvent.CURRENT_SCHEMA_VERSION,
                Instant.now(),
                /* documentId */ null,
                /* pipelineRunId */ null,
                nodeRunId,
                traceparent,
                Actor.system(serviceName, serviceVersion, instanceId),
                /* resource */ null,
                /* action */ "EXTRACT",
                Outcome.FAILURE,
                details,
                /* retentionClass */ null,
                /* previousEventHash */ null);
    }

    private static String eventId() {
        // 26 uppercase hex chars from a fresh UUID — satisfies the
        // envelope schema's Crockford-base32 pattern (which excludes
        // I L O U; hex digits + A–F use none of those). Sortability
        // is sacrificed; switch to a real ULID once the utility lands.
        return UUID.randomUUID().toString().replace("-", "").toUpperCase().substring(0, 26);
    }
}
