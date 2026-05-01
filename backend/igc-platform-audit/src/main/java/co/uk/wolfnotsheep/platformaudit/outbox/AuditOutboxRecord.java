package co.uk.wolfnotsheep.platformaudit.outbox;

import co.uk.wolfnotsheep.platformaudit.envelope.AuditEvent;
import co.uk.wolfnotsheep.platformaudit.envelope.Tier;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * MongoDB-mapped row in {@code audit_outbox}. Carries the envelope through
 * the transactional-outbox handoff to the relay.
 *
 * <p>Indexes are managed by Mongock — see
 * {@code V002_AuditOutboxIndexes} in {@code igc-app-assembly}:
 *
 * <ul>
 *     <li>{@code idx_status_nextRetry} on {@code (status, nextRetryAt)}.</li>
 *     <li>{@code idx_eventId_unique} on {@code eventId}.</li>
 *     <li>{@code idx_createdAt} on {@code createdAt}.</li>
 * </ul>
 *
 * @param id           Mongo-assigned outbox-row id (separate from envelope's eventId).
 * @param eventId      Envelope eventId; unique-indexed.
 * @param tier         Envelope tier — drives the target Rabbit channel.
 * @param eventType    Envelope eventType — drives the routing key suffix.
 * @param envelope     The full envelope. Validated against
 *                     {@code event-envelope.schema.json} at write time (validation
 *                     wiring lands with the auto-config in a follow-up PR).
 * @param status       PENDING / PUBLISHED / FAILED.
 * @param attempts     Publish attempt count.
 * @param lastError    Last error message; populated when {@code status=FAILED} or after a retry.
 * @param createdAt    When the row was written.
 * @param publishedAt  When the relay successfully published.
 * @param nextRetryAt  When the relay should retry (for backoff). Same as createdAt for new rows.
 */
@Document(collection = "audit_outbox")
public record AuditOutboxRecord(
        @Id String id,
        String eventId,
        Tier tier,
        String eventType,
        AuditEvent envelope,
        OutboxStatus status,
        int attempts,
        String lastError,
        Instant createdAt,
        Instant publishedAt,
        Instant nextRetryAt
) {

    /** Constructs a fresh PENDING outbox row from an envelope. */
    public static AuditOutboxRecord pendingFor(AuditEvent envelope) {
        Instant now = Instant.now();
        return new AuditOutboxRecord(
                null,
                envelope.eventId(),
                envelope.tier(),
                envelope.eventType(),
                envelope,
                OutboxStatus.PENDING,
                0,
                null,
                now,
                null,
                now
        );
    }
}
