package co.uk.wolfnotsheep.auditcollector.consumer;

import co.uk.wolfnotsheep.auditcollector.store.StoredTier1Event;
import co.uk.wolfnotsheep.auditcollector.store.StoredTier2Event;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Map;

/**
 * Extracts the denormalised fields that the {@code Stored*Event} rows
 * index on, from the free-form envelope map the consumer receives.
 * Mirrors {@code contracts/audit/event-envelope.schema.json} — see
 * the schema for field semantics.
 *
 * <p>The full envelope is preserved verbatim on
 * {@code StoredAuditEvent.envelope} so the contract response can
 * round-trip the raw payload regardless of which fields we
 * happen to denormalise today.
 */
public final class EnvelopeMapper {

    private EnvelopeMapper() { }

    public static StoredTier1Event toTier1(Map<String, Object> envelope) {
        return new StoredTier1Event(
                str(envelope, "eventId"),
                str(envelope, "eventType"),
                str(envelope, "schemaVersion"),
                instant(envelope, "timestamp"),
                str(envelope, "documentId"),
                str(envelope, "pipelineRunId"),
                str(envelope, "nodeRunId"),
                str(envelope, "traceparent"),
                actorService(envelope),
                actorType(envelope),
                resourceField(envelope, "type"),
                resourceField(envelope, "id"),
                str(envelope, "action"),
                str(envelope, "outcome"),
                str(envelope, "retentionClass"),
                str(envelope, "previousEventHash"),
                envelope);
    }

    public static StoredTier2Event toTier2(Map<String, Object> envelope) {
        return new StoredTier2Event(
                str(envelope, "eventId"),
                str(envelope, "eventType"),
                str(envelope, "schemaVersion"),
                instant(envelope, "timestamp"),
                str(envelope, "documentId"),
                str(envelope, "pipelineRunId"),
                str(envelope, "nodeRunId"),
                str(envelope, "traceparent"),
                actorService(envelope),
                actorType(envelope),
                resourceField(envelope, "type"),
                resourceField(envelope, "id"),
                str(envelope, "action"),
                str(envelope, "outcome"),
                str(envelope, "retentionClass"),
                envelope);
    }

    private static String str(Map<String, Object> map, String key) {
        if (map == null) return null;
        Object v = map.get(key);
        return v == null ? null : v.toString();
    }

    @SuppressWarnings("unchecked")
    private static String actorService(Map<String, Object> envelope) {
        if (envelope == null) return null;
        Object actor = envelope.get("actor");
        if (actor instanceof Map<?, ?> m) {
            Object v = ((Map<String, Object>) m).get("service");
            return v == null ? null : v.toString();
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static String actorType(Map<String, Object> envelope) {
        if (envelope == null) return null;
        Object actor = envelope.get("actor");
        if (actor instanceof Map<?, ?> m) {
            Object v = ((Map<String, Object>) m).get("type");
            return v == null ? null : v.toString();
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static String resourceField(Map<String, Object> envelope, String key) {
        if (envelope == null) return null;
        Object resource = envelope.get("resource");
        if (resource instanceof Map<?, ?> m) {
            Object v = ((Map<String, Object>) m).get(key);
            return v == null ? null : v.toString();
        }
        return null;
    }

    private static Instant instant(Map<String, Object> map, String key) {
        String s = str(map, key);
        if (s == null) return null;
        try {
            return Instant.parse(s);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
