package co.uk.wolfnotsheep.auditcollector.store;

/**
 * Thrown by {@link Tier1Store#append} when an {@code eventId} that
 * already exists is re-submitted. The collector's consumer treats
 * this as an idempotent no-op (per CLAUDE.md "Audit Relay Pattern" —
 * "Idempotent re-emission of the same event is a no-op upsert").
 *
 * <p>Distinct from arbitrary backend errors so the consumer can tell
 * "duplicate" apart from "Mongo unreachable" without inspecting
 * Spring exception classes.
 */
public class AppendOnlyViolationException extends RuntimeException {
    public AppendOnlyViolationException(String eventId) {
        super("Tier 1 event " + eventId + " already exists — append-only store rejects overwrite");
    }
}
