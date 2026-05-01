package co.uk.wolfnotsheep.platformconfig.event;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * Cache-invalidation event published when a governance entity is mutated.
 * Mirrors {@code ConfigChangedEvent} in
 * {@code contracts/messaging/asyncapi.yaml}.
 *
 * <p>Per CSV #30: every write that mutates a governance entity
 * (taxonomy, PII patterns, retention schedules, sensitivity definitions,
 * metadata schemas, storage tiers, traits, governance policies, pipeline
 * blocks) fires one of these so all replicas drop the relevant cache
 * entries. Empty {@link #entityIds} is the bulk signal — invalidate the
 * whole cache for that {@code entityType}.
 *
 * @param entityType  Uppercase snake-case identifier for the entity
 *                    family (e.g. {@code TAXONOMY}, {@code POLICY},
 *                    {@code PII_PATTERN_SET}, {@code RETENTION_SCHEDULE},
 *                    {@code METADATA_SCHEMA}, {@code STORAGE_TIER},
 *                    {@code TRAIT}, {@code BLOCK}).
 * @param entityIds   Specific entity IDs that changed; empty / null
 *                    means bulk wildcard. Stored as a defensive copy.
 * @param changeType  CREATED / UPDATED / DELETED.
 * @param timestamp   RFC 3339 producer-side timestamp.
 * @param actor       Service identifier that fired the event.
 * @param traceparent W3C trace context per CSV #20; nullable.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ConfigChangedEvent(
        String entityType,
        List<String> entityIds,
        ChangeType changeType,
        Instant timestamp,
        String actor,
        String traceparent
) {

    public ConfigChangedEvent {
        if (entityType == null || entityType.isBlank()) {
            throw new IllegalArgumentException("entityType is required");
        }
        if (changeType == null) {
            throw new IllegalArgumentException("changeType is required");
        }
        if (timestamp == null) {
            throw new IllegalArgumentException("timestamp is required");
        }
        if (actor == null || actor.isBlank()) {
            throw new IllegalArgumentException("actor is required");
        }
        entityIds = entityIds == null ? List.of() : List.copyOf(entityIds);
    }

    /** Convenience: a bulk-wildcard event (all entries of {@code entityType}). */
    public static ConfigChangedEvent bulk(String entityType, ChangeType changeType, String actor) {
        return new ConfigChangedEvent(entityType, List.of(), changeType, Instant.now(), actor, null);
    }

    /** Convenience: a single-entity event. */
    public static ConfigChangedEvent single(String entityType, String entityId, ChangeType changeType, String actor) {
        return new ConfigChangedEvent(entityType, List.of(entityId), changeType, Instant.now(), actor, null);
    }

    @JsonIgnore
    public boolean isBulk() {
        return entityIds.isEmpty();
    }
}
