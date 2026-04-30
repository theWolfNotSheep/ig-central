package co.uk.wolfnotsheep.auditcollector.consumer;

import co.uk.wolfnotsheep.auditcollector.chain.ChainBrokenException;
import co.uk.wolfnotsheep.auditcollector.chain.EventHasher;
import co.uk.wolfnotsheep.auditcollector.store.AppendOnlyViolationException;
import co.uk.wolfnotsheep.auditcollector.store.StoredTier1Event;
import co.uk.wolfnotsheep.auditcollector.store.Tier1Store;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * Consumes {@code audit.tier1.*} from the worker's bound queue
 * (see {@link AuditRabbitConfig}). Validates the per-resource hash
 * chain (CSV #4) on receipt — events whose {@code previousEventHash}
 * doesn't match the recomputed hash of the latest stored event for
 * the same resource are rejected (logged + acked, not requeued).
 *
 * <p>Storage dispatches through {@link Tier1Store}; the active
 * backend (Mongo append-only today; S3 Object Lock in a future PR)
 * is chosen by {@code gls.audit.collector.tier1-backend}.
 *
 * <p>Per CLAUDE.md happy/unhappy-path:
 * <ul>
 *   <li>{@link ChainBrokenException} → log error, ack the message
 *       (don't infinite-requeue a permanently broken chain). A real
 *       deployment binds a DLX to capture for forensics; that's a
 *       Phase 2 reliability follow-up.</li>
 *   <li>{@link AppendOnlyViolationException} → already persisted;
 *       idempotent no-op.</li>
 *   <li>Other unexpected → rethrow so AMQP requeues per broker config.</li>
 * </ul>
 */
@Component
public class Tier1Consumer {

    private static final Logger log = LoggerFactory.getLogger(Tier1Consumer.class);

    private final Tier1Store tier1Store;

    public Tier1Consumer(Tier1Store tier1Store) {
        this.tier1Store = tier1Store;
    }

    @RabbitListener(queues = AuditRabbitConfig.QUEUE_TIER1)
    public void onTier1(Map<String, Object> envelope) {
        if (envelope == null) {
            log.warn("tier1 consumer: null envelope — discarding");
            return;
        }
        StoredTier1Event row = EnvelopeMapper.toTier1(envelope);
        if (row.getEventId() == null || row.getResourceType() == null || row.getResourceId() == null) {
            log.warn("tier1 consumer: envelope missing required fields (eventId={}, resourceType={}, resourceId={}) — discarding",
                    row.getEventId(), row.getResourceType(), row.getResourceId());
            return;
        }

        try {
            validateChain(row);
            tier1Store.append(row);
            log.debug("tier1 stored eventId={} resource={}:{}",
                    row.getEventId(), row.getResourceType(), row.getResourceId());
        } catch (ChainBrokenException e) {
            log.error("tier1 chain broken: {}", e.getMessage());
            // ack — broken chain is permanent state; don't requeue
        } catch (AppendOnlyViolationException e) {
            log.debug("tier1 idempotent: eventId {} already persisted", row.getEventId());
        }
    }

    private void validateChain(StoredTier1Event incoming) {
        Optional<StoredTier1Event> latest = tier1Store.findLatestForResource(
                incoming.getResourceType(), incoming.getResourceId());

        String expected = incoming.getPreviousEventHash();
        String computed = latest.map(EventHasher::hashOf).orElse(null);

        if (!eq(expected, computed)) {
            throw new ChainBrokenException(
                    incoming.getResourceType(), incoming.getResourceId(),
                    expected, computed, incoming.getEventId());
        }
    }

    private static boolean eq(String a, String b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.equals(b);
    }
}
