package co.uk.wolfnotsheep.auditcollector.consumer;

import co.uk.wolfnotsheep.auditcollector.store.StoredTier2Event;
import co.uk.wolfnotsheep.auditcollector.store.Tier2Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Consumes {@code audit.tier2.*} from the worker's bound queue.
 * No chain validation — Tier 2 is the operational store.
 * Idempotent on {@code eventId} (Mongo unique-key conflict → no-op).
 */
@Component
public class Tier2Consumer {

    private static final Logger log = LoggerFactory.getLogger(Tier2Consumer.class);

    private final Tier2Repository tier2Repo;

    public Tier2Consumer(Tier2Repository tier2Repo) {
        this.tier2Repo = tier2Repo;
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
        try {
            tier2Repo.insert(row);
            log.debug("tier2 stored eventId={} eventType={}", row.getEventId(), row.getEventType());
        } catch (DuplicateKeyException e) {
            log.debug("tier2 idempotent: eventId {} already persisted", row.getEventId());
        }
    }
}
