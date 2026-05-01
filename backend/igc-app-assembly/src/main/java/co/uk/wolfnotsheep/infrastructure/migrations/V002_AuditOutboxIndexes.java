package co.uk.wolfnotsheep.infrastructure.migrations;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;

/**
 * Creates indexes for the {@code audit_outbox} collection used by the
 * transactional-outbox audit relay pattern (see {@code CLAUDE.md} →
 * Audit Relay Pattern; per architecture §7.7).
 *
 * <p>The collection itself is created lazily on first write — MongoDB
 * auto-creates collections when an index or document is added to them.
 *
 * <h3>Indexes</h3>
 * <ul>
 *     <li>{@code idx_status_nextRetry} on {@code status, nextRetryAt} —
 *         the relay's primary query (find PENDING and retry-eligible
 *         FAILED rows, oldest first).</li>
 *     <li>{@code idx_eventId_unique} on {@code eventId} — uniqueness
 *         guard so the library's idempotent re-emission stays a no-op
 *         even under in-flight failure / retry loops.</li>
 *     <li>{@code idx_createdAt} on {@code createdAt} — supports
 *         retention / cleanup and chronological diagnostics queries.</li>
 * </ul>
 *
 * <p>The {@code AuditOutboxRecord} POJO that maps the document shape
 * lives in the upcoming {@code igc-platform-audit} shared library.
 */
@ChangeUnit(id = "audit-outbox-indexes", order = "002", author = "ig-central")
public class V002_AuditOutboxIndexes {

    private static final String COLLECTION = "audit_outbox";
    private static final String IDX_STATUS_NEXT_RETRY = "idx_status_nextRetry";
    private static final String IDX_EVENT_ID_UNIQUE = "idx_eventId_unique";
    private static final String IDX_CREATED_AT = "idx_createdAt";

    private static final Logger log = LoggerFactory.getLogger(V002_AuditOutboxIndexes.class);

    @Execution
    public void execution(MongoTemplate mongoTemplate) {
        IndexOperations indexes = mongoTemplate.indexOps(COLLECTION);

        indexes.ensureIndex(new Index()
                .on("status", Sort.Direction.ASC)
                .on("nextRetryAt", Sort.Direction.ASC)
                .named(IDX_STATUS_NEXT_RETRY));

        indexes.ensureIndex(new Index()
                .on("eventId", Sort.Direction.ASC)
                .unique()
                .named(IDX_EVENT_ID_UNIQUE));

        indexes.ensureIndex(new Index()
                .on("createdAt", Sort.Direction.ASC)
                .named(IDX_CREATED_AT));

        log.info("audit_outbox indexes ensured: {}, {}, {}",
                IDX_STATUS_NEXT_RETRY, IDX_EVENT_ID_UNIQUE, IDX_CREATED_AT);
    }

    @RollbackExecution
    public void rollbackExecution(MongoTemplate mongoTemplate) {
        IndexOperations indexes = mongoTemplate.indexOps(COLLECTION);
        indexes.dropIndex(IDX_STATUS_NEXT_RETRY);
        indexes.dropIndex(IDX_EVENT_ID_UNIQUE);
        indexes.dropIndex(IDX_CREATED_AT);
        log.info("audit_outbox indexes rolled back");
    }
}
