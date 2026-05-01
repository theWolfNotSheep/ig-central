package co.uk.wolfnotsheep.extraction.archive.audit;

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
 * Builds the Tier 2 audit envelopes the archive service emits. Pure
 * factory — no Spring, no Mongo, no Rabbit. Mirrors
 * {@code igc-extraction-tika}'s {@code ExtractionEvents} so the two
 * services share the same audit conventions.
 *
 * <p>Per CSV #5 / asyncapi {@code audit.tier2.{eventType}}: archive
 * publishes only Tier 2 events ({@code SYSTEM} tier) — operational
 * records of unpack outcomes. Document-anchored Tier 1 events are
 * emitted by other services that own the document lifecycle (the
 * orchestrator emits {@code DOCUMENT_INGESTED} for each child after
 * commit; this service is just the parser).
 *
 * <p>The {@code action} stays {@code EXTRACT} so the audit stream is
 * homogeneous across the extraction family — readers filtering by
 * {@code action="EXTRACT"} catch tika + archive + future ocr/audio
 * with one query. The metadata distinguishes archive specifics
 * ({@code archiveType}, {@code childCount}).
 */
public final class ArchiveEvents {

    public static final String EVENT_TYPE_COMPLETED = "EXTRACTION_COMPLETED";
    public static final String EVENT_TYPE_FAILED = "EXTRACTION_FAILED";

    private ArchiveEvents() {
    }

    public static AuditEvent completed(
            String serviceName,
            String serviceVersion,
            String instanceId,
            String nodeRunId,
            String traceparent,
            String archiveType,
            String detectedMimeType,
            int childCount,
            long byteCount,
            long durationMs) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("nodeRunId", nodeRunId);
        if (archiveType != null) {
            metadata.put("archiveType", archiveType);
        }
        if (detectedMimeType != null) {
            metadata.put("detectedMimeType", detectedMimeType);
        }
        metadata.put("childCount", childCount);
        metadata.put("byteCount", byteCount);
        metadata.put("durationMs", durationMs);

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
        return UUID.randomUUID().toString().replace("-", "").toUpperCase().substring(0, 26);
    }
}
