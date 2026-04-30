package co.uk.wolfnotsheep.auditcollector.consumer;

import co.uk.wolfnotsheep.auditcollector.chain.ChainBrokenException;
import co.uk.wolfnotsheep.auditcollector.chain.EventHasher;
import co.uk.wolfnotsheep.auditcollector.store.AppendOnlyViolationException;
import co.uk.wolfnotsheep.auditcollector.store.StoredTier1Event;
import co.uk.wolfnotsheep.auditcollector.store.Tier1Store;
import com.rabbitmq.client.GetResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
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
 * <p><strong>Phase 2.4 — single-leader semantics via ShedLock.</strong>
 * Tier 1 events form per-resource hash chains; multi-replica
 * consumption would race on validate-then-append and could create
 * forks in the chain. This class polls the queue inside a
 * {@code @SchedulerLock("audit-tier1-leader")} scheduled method —
 * only one replica drains the queue at any moment, the others sit
 * idle and try again on the next tick. On leader death the lock
 * auto-releases after {@code lockAtMostFor} and the next tick
 * promotes a replacement.
 *
 * <p>Per CLAUDE.md happy/unhappy-path:
 * <ul>
 *   <li>{@link ChainBrokenException} → log error, ack the message
 *       (don't infinite-requeue a permanently broken chain). A real
 *       deployment binds a DLX to capture for forensics.</li>
 *   <li>{@link AppendOnlyViolationException} → already persisted;
 *       idempotent no-op.</li>
 *   <li>Other unexpected → log + ack (we never want a poisoned
 *       message to wedge the leader replica forever).</li>
 * </ul>
 */
@Component
public class Tier1Consumer {

    private static final Logger log = LoggerFactory.getLogger(Tier1Consumer.class);

    private final Tier1Store tier1Store;
    private final ObjectProvider<RabbitTemplate> rabbitTemplateProvider;
    private final ObjectProvider<MessageConverter> messageConverterProvider;
    private final ObjectProvider<MeterRegistry> meterRegistryProvider;

    /** Test-friendly no-arg-style constructor — the existing tests inject only the store. */
    public Tier1Consumer(Tier1Store tier1Store) {
        this(tier1Store, /* rabbit */ null, /* converter */ null, /* metrics */ null);
    }

    public Tier1Consumer(Tier1Store tier1Store,
                         ObjectProvider<RabbitTemplate> rabbitTemplateProvider,
                         ObjectProvider<MessageConverter> messageConverterProvider,
                         ObjectProvider<MeterRegistry> meterRegistryProvider) {
        this.tier1Store = tier1Store;
        this.rabbitTemplateProvider = rabbitTemplateProvider;
        this.messageConverterProvider = messageConverterProvider;
        this.meterRegistryProvider = meterRegistryProvider;
    }

    /**
     * Poll the Tier 1 queue under leader-election. Default cadence
     * 5 s; tune via {@code gls.audit.collector.tier1.poll-interval}.
     * The {@code lockAtMostFor} caps how long a stuck replica can
     * hold leadership; default 1 minute.
     */
    @Scheduled(
            fixedDelayString = "${gls.audit.collector.tier1.poll-interval:PT5S}",
            initialDelayString = "${gls.audit.collector.tier1.initial-delay:PT5S}")
    @SchedulerLock(name = "audit-tier1-leader",
            lockAtMostFor = "${gls.audit.collector.tier1.lock-at-most-for:PT1M}",
            lockAtLeastFor = "${gls.audit.collector.tier1.lock-at-least-for:PT0S}")
    public void pollTier1() {
        if (rabbitTemplateProvider == null) return;  // test-only constructor path
        RabbitTemplate rabbit = rabbitTemplateProvider.getIfAvailable();
        MessageConverter converter = messageConverterProvider == null ? null : messageConverterProvider.getIfAvailable();
        if (rabbit == null || converter == null) {
            log.debug("Tier 1 leader poll: RabbitTemplate or MessageConverter not yet wired — skipping cycle");
            return;
        }
        int processed = 0;
        while (true) {
            Boolean drained = rabbit.<Boolean>execute(channel -> {
                GetResponse resp = channel.basicGet(AuditRabbitConfig.QUEUE_TIER1, /* autoAck */ false);
                if (resp == null) return false;
                long deliveryTag = resp.getEnvelope().getDeliveryTag();
                try {
                    Map<String, Object> envelope = convertBody(converter, resp);
                    if (envelope != null) {
                        onTier1(envelope);
                        recordOutcome("processed");
                    } else {
                        recordOutcome("unconvertible");
                    }
                    channel.basicAck(deliveryTag, false);
                    return true;
                } catch (Exception e) {
                    log.error("Tier 1 poll: unexpected error processing message: {}", e.getMessage(), e);
                    recordOutcome("error");
                    // Ack regardless — a poison message must not wedge the leader.
                    channel.basicAck(deliveryTag, false);
                    return true;
                }
            });
            if (drained == null || !drained) break;
            processed++;
        }
        if (processed > 0) {
            log.debug("Tier 1 leader poll: drained {} message(s)", processed);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> convertBody(MessageConverter converter, GetResponse resp) {
        MessageProperties props = new MessageProperties();
        if (resp.getProps() != null && resp.getProps().getContentType() != null) {
            props.setContentType(resp.getProps().getContentType());
        } else {
            props.setContentType("application/json");
        }
        Object converted = converter.fromMessage(new Message(resp.getBody(), props));
        if (converted instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return null;
    }

    /**
     * Per-message handler. Public so the existing unit tests can call
     * it directly without standing up Rabbit.
     */
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

    private void recordOutcome(String outcome) {
        if (meterRegistryProvider == null) return;
        MeterRegistry registry = meterRegistryProvider.getIfAvailable();
        if (registry == null) return;
        Counter.builder("audit.tier1.consumer")
                .description("Tier 1 audit-collector poll outcomes")
                .tags(Tags.of("outcome", outcome))
                .register(registry)
                .increment();
    }
}
