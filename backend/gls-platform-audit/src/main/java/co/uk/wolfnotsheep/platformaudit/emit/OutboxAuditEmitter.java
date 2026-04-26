package co.uk.wolfnotsheep.platformaudit.emit;

import co.uk.wolfnotsheep.platformaudit.envelope.AuditEvent;
import co.uk.wolfnotsheep.platformaudit.outbox.AuditOutboxRecord;
import co.uk.wolfnotsheep.platformaudit.outbox.AuditOutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Default {@link AuditEmitter} implementation. Writes the envelope to the
 * {@code audit_outbox} collection.
 *
 * <p>Idempotent on {@code envelope.eventId()} — relies on the unique index
 * {@code idx_eventId_unique} to short-circuit duplicates. The relay's
 * at-least-once delivery downstream uses the same eventId for dedup.
 *
 * <p>Schema validation against {@code event-envelope.schema.json} is not
 * enforced here yet; it lands in a follow-up PR alongside the
 * auto-configuration that turns this library into a Spring Boot starter.
 * Until then, callers must construct valid envelopes themselves.
 */
@Component
public class OutboxAuditEmitter implements AuditEmitter {

    private static final Logger log = LoggerFactory.getLogger(OutboxAuditEmitter.class);

    private final AuditOutboxRepository outboxRepository;

    public OutboxAuditEmitter(AuditOutboxRepository outboxRepository) {
        this.outboxRepository = outboxRepository;
    }

    @Override
    public void emit(AuditEvent envelope) {
        outboxRepository.findByEventId(envelope.eventId()).ifPresentOrElse(
                existing -> log.debug("audit outbox: eventId {} already present (idempotent no-op)",
                        envelope.eventId()),
                () -> {
                    AuditOutboxRecord row = AuditOutboxRecord.pendingFor(envelope);
                    outboxRepository.save(row);
                    log.debug("audit outbox: enqueued eventId={} tier={} eventType={}",
                            envelope.eventId(), envelope.tier(), envelope.eventType());
                });
    }
}
