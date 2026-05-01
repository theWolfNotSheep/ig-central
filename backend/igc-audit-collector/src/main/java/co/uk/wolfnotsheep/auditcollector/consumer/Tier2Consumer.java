package co.uk.wolfnotsheep.auditcollector.consumer;

import co.uk.wolfnotsheep.auditcollector.store.StoredTier2Event;
import co.uk.wolfnotsheep.auditcollector.store.Tier2Store;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Consumes {@code audit.tier2.*} from the worker's bound queue.
 * No chain validation — Tier 2 is the operational store. Storage
 * dispatches through {@link Tier2Store}; the active backend
 * (Mongo / ES) is chosen by
 * {@code igc.audit.collector.tier2-backend} per the PR3 cutover
 * pattern. Idempotency is the backend's responsibility — the
 * interface contract demands {@code save} be a no-op on duplicate
 * {@code eventId}.
 */
@Component
public class Tier2Consumer {

    private static final Logger log = LoggerFactory.getLogger(Tier2Consumer.class);

    private final Tier2Store tier2Store;

    public Tier2Consumer(Tier2Store tier2Store) {
        this.tier2Store = tier2Store;
    }

    @RabbitListener(queues = AuditRabbitConfig.QUEUE_TIER2)
    public void onTier2(Map<String, Object> envelope) {
        if (envelope == null) {
            log.warn("tier2 consumer: null envelope — discarding");
            return;
        }
        StoredTier2Event row = EnvelopeMapper.toTier2(envelope);
        if (row.getEventId() == null) {
            log.warn("tier2 consumer: envelope missing eventId — discarding");
            return;
        }
        tier2Store.save(row);
        log.debug("tier2 stored eventId={} eventType={}", row.getEventId(), row.getEventType());
    }
}
