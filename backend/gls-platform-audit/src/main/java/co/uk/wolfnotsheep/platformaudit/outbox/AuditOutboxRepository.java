package co.uk.wolfnotsheep.platformaudit.outbox;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data Mongo repository for {@link AuditOutboxRecord}.
 *
 * <p>The relay (follow-up PR) will use {@link #findByStatusAndNextRetryAtBeforeOrderByCreatedAtAsc}
 * as its primary poll query — supported by the {@code idx_status_nextRetry} index.
 */
public interface AuditOutboxRepository extends MongoRepository<AuditOutboxRecord, String> {

    /** Idempotent re-emission lookup. Backed by {@code idx_eventId_unique}. */
    Optional<AuditOutboxRecord> findByEventId(String eventId);

    /**
     * Relay's primary query: rows in the given status whose retry time has come,
     * oldest first. Backed by {@code idx_status_nextRetry}.
     */
    List<AuditOutboxRecord> findByStatusAndNextRetryAtBeforeOrderByCreatedAtAsc(
            OutboxStatus status, Instant cutoff);
}
