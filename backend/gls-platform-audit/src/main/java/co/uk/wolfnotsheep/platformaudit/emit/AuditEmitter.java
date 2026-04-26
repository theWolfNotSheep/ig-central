package co.uk.wolfnotsheep.platformaudit.emit;

import co.uk.wolfnotsheep.platformaudit.envelope.AuditEvent;

/**
 * Single entry point every service uses to emit audit events.
 *
 * <p>Implementations write the envelope to the {@code audit_outbox} collection
 * <em>in the same Mongo transaction</em> as the originating state change. The
 * relay (follow-up PR) is the only thing that publishes to Rabbit — services
 * never publish audit events directly.
 *
 * <p>See {@code CLAUDE.md} → Audit Relay Pattern for the full rules.
 */
public interface AuditEmitter {

    /**
     * Persist the envelope to the outbox. Idempotent on {@code envelope.eventId()}:
     * a duplicate eventId resolves to the existing row without raising.
     *
     * <p>Must be called inside the same Mongo transaction as the originating
     * state change. If the surrounding transaction rolls back, the outbox write
     * rolls back with it — exactly the property the outbox pattern depends on.
     *
     * @param envelope a fully-constructed envelope; the implementation does not
     *                 mutate it.
     * @throws IllegalArgumentException if the envelope fails validation against
     *                                  {@code event-envelope.schema.json} (validation
     *                                  wiring lands with the auto-config in a
     *                                  follow-up PR).
     */
    void emit(AuditEvent envelope);
}
