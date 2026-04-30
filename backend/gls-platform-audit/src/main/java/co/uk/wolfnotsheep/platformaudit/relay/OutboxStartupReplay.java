package co.uk.wolfnotsheep.platformaudit.relay;

import co.uk.wolfnotsheep.platformaudit.outbox.AuditOutboxRecord;
import co.uk.wolfnotsheep.platformaudit.outbox.OutboxStatus;
import com.mongodb.client.result.UpdateResult;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Instant;

/**
 * Phase 2.1 PR3 — drain any unacked audit-outbox rows on application restart.
 *
 * <p>The {@link OutboxRelay}'s {@code @Scheduled} poll already picks up
 * {@code PENDING} rows whose {@code nextRetryAt} is past — including
 * immediately on the first poll after startup. The gap this component
 * closes: rows still in backoff (with {@code nextRetryAt} in the future)
 * sit waiting after a restart, even though the underlying transient
 * failure that caused the backoff may already be resolved.
 *
 * <p>On {@link ApplicationReadyEvent}, this listener bulk-updates every
 * {@code PENDING} row whose {@code nextRetryAt} is in the future, setting
 * {@code nextRetryAt = now} so the relay's next poll picks it up. The
 * {@code attempts} counter is preserved — persistent failures still hit
 * {@code maxAttempts} and transition to {@code FAILED} as before; we just
 * compress the wall-clock time between attempts.
 *
 * <p>Why not also reset {@code FAILED} rows: those exceeded
 * {@code maxAttempts} for a reason. Auto-resurrecting them risks
 * cascading-failure loops if the underlying issue persists. A separate
 * admin-triggered "replay FAILED" action is the right shape for that
 * (future PR).
 *
 * <p>Idempotent across multi-replica deploys — each replica's startup
 * fires this listener; subsequent calls find no rows still in backoff
 * (the first replica reset them) so they're no-ops.
 */
public class OutboxStartupReplay {

    private static final Logger log = LoggerFactory.getLogger(OutboxStartupReplay.class);

    private final MongoTemplate mongoTemplate;
    private final ObjectProvider<MeterRegistry> meterRegistryProvider;

    public OutboxStartupReplay(MongoTemplate mongoTemplate,
                               ObjectProvider<MeterRegistry> meterRegistryProvider) {
        this.mongoTemplate = mongoTemplate;
        this.meterRegistryProvider = meterRegistryProvider;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        try {
            replayBackedOffPendingRows();
        } catch (Exception e) {
            // Never let startup replay break the app boot.
            log.error("Audit outbox startup replay failed: {}", e.getMessage(), e);
        }
    }

    /** Visible for testing. */
    long replayBackedOffPendingRows() {
        Instant now = Instant.now();
        Query backedOffPending = Query.query(Criteria.where("status").is(OutboxStatus.PENDING)
                .and("nextRetryAt").gt(now));
        Update reset = new Update().set("nextRetryAt", now);

        UpdateResult result = mongoTemplate.updateMulti(backedOffPending, reset, AuditOutboxRecord.class);
        long modified = result.getModifiedCount();

        if (modified > 0) {
            log.info("Audit outbox startup replay: reset nextRetryAt on {} backed-off PENDING row(s) for immediate retry",
                    modified);
            recordCounter("audit.outbox.startup_replay.reset", modified);
        } else {
            log.info("Audit outbox startup replay: 0 backed-off PENDING rows (clean state)");
        }
        return modified;
    }

    private void recordCounter(String name, long delta) {
        if (delta <= 0) return;
        MeterRegistry registry = meterRegistryProvider.getIfAvailable();
        if (registry == null) return;
        Counter.builder(name)
                .description("Audit outbox startup replay — count of backed-off PENDING rows reset to immediate retry")
                .register(registry)
                .increment(delta);
    }
}
