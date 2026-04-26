package co.uk.wolfnotsheep.platformaudit.envelope;

import java.time.Instant;

/**
 * The audit event envelope. Single contract for every audit event written
 * by every container; mirrors {@code contracts/audit/event-envelope.schema.json}.
 *
 * <p>Per architecture §7.4 / CSV #4 / #6 / #7 / #20:
 *
 * <ul>
 *     <li>{@code eventId} is a ULID (Crockford base32, 26 chars, time-sortable).</li>
 *     <li>{@code tier} = DOMAIN (Tier 1, compliance) or SYSTEM (Tier 2, ops).</li>
 *     <li>Tier 1 envelopes MUST carry {@code previousEventHash} (or null for first-in-chain),
 *         {@code resource}, and {@code retentionClass}. The library does not enforce this
 *         construction-side — JSON-schema validation in the relay is the gate.</li>
 *     <li>{@code traceparent} propagates W3C trace context.</li>
 * </ul>
 *
 * @param eventId           ULID (26-char Crockford base32).
 * @param eventType         Event family (e.g. {@code DOCUMENT_CLASSIFIED}, {@code MCP_TOOL_CALLED}).
 * @param tier              DOMAIN / SYSTEM.
 * @param schemaVersion     Envelope schema version (e.g. {@code 1.0.0}).
 * @param timestamp         RFC 3339 timestamp from the producing service.
 * @param documentId        Convenience correlation key for document-anchored events; nullable.
 * @param pipelineRunId     Pipeline run id; nullable.
 * @param nodeRunId         Node run id within the pipeline; nullable.
 * @param traceparent       W3C trace context; nullable.
 * @param actor             Who / what performed the action.
 * @param resource          The thing the action acted on; required for Tier 1.
 * @param action            Verb describing what happened.
 * @param outcome           SUCCESS / FAILURE / PARTIAL.
 * @param details           Event-specific payload (metadata + content partition).
 * @param retentionClass    {@code 7Y} / {@code 90D} / {@code 30D}; required for Tier 1.
 * @param previousEventHash Hash of the prior event in this resource's chain;
 *                          {@code null} for first-in-chain. Tier 1 only.
 */
public record AuditEvent(
        String eventId,
        String eventType,
        Tier tier,
        String schemaVersion,
        Instant timestamp,
        String documentId,
        String pipelineRunId,
        String nodeRunId,
        String traceparent,
        Actor actor,
        Resource resource,
        String action,
        Outcome outcome,
        AuditDetails details,
        String retentionClass,
        String previousEventHash
) {

    /**
     * Default schema version emitted by the library. Bump when the envelope
     * schema in {@code contracts/audit/event-envelope.schema.json} bumps.
     */
    public static final String CURRENT_SCHEMA_VERSION = "1.0.0";
}
