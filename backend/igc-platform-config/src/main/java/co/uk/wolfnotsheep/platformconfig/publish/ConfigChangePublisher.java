package co.uk.wolfnotsheep.platformconfig.publish;

import co.uk.wolfnotsheep.platformconfig.event.ChangeType;
import co.uk.wolfnotsheep.platformconfig.event.ConfigChangedEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Instant;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Publishes {@link ConfigChangedEvent}s to the {@code igc.config}
 * topic exchange under routing key {@code config.changed}. Single
 * emitter — services don't call {@link RabbitTemplate#convertAndSend(String, String, Object)}
 * for cache invalidation directly.
 *
 * <p>Per CSV #30, both local writes (admin UI, API mutations) and
 * Hub-driven imports ({@code PackImportService}) MUST publish here so
 * the contract is single-channel regardless of where the change came
 * from.
 *
 * <p><strong>Phase 2.1 PR4 — retry on AMQP failure.</strong> Failed
 * publishes go into a bounded in-memory retry buffer. A scheduled
 * {@link #flushRetryBuffer()} drains the buffer at a fixed interval
 * (default 30 s). When the broker recovers, queued events go through
 * on the next flush.
 *
 * <p>Retry guarantees:
 * <ul>
 *   <li><strong>Bounded buffer.</strong> Default cap is 1024 events;
 *       when full, oldest events are dropped (and a counter increments)
 *       to make room for newer ones.</li>
 *   <li><strong>Stop on first failure.</strong> The flush loop stops
 *       at the first AMQP failure so a stuck broker doesn't burn
 *       through the whole buffer.</li>
 *   <li><strong>Single in-flight flush.</strong> {@code @Scheduled}
 *       won't overlap, but a re-entrant call from a sync publish
 *       inside a flush is guarded by an {@link AtomicBoolean} latch.</li>
 *   <li><strong>Best-effort, not durable.</strong> A pod crash loses
 *       the buffered events. For the cache-invalidation contract this
 *       is acceptable — the next mutation of the same entity self-heals.
 *       For stronger guarantees (hub pack imports of many entities),
 *       a durable outbox like {@code audit_outbox} is the next step
 *       and is tracked as a follow-up.</li>
 * </ul>
 */
public class ConfigChangePublisher {

    private static final Logger log = LoggerFactory.getLogger(ConfigChangePublisher.class);
    private static final String EXCHANGE = "igc.config";
    private static final String ROUTING_KEY = "config.changed";

    /** Default buffer cap when no override is configured. Tunable via constructor. */
    public static final int DEFAULT_RETRY_BUFFER_SIZE = 1024;

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper mapper;
    private final String actor;
    private final int retryBufferMaxSize;
    private final Deque<ConfigChangedEvent> retryBuffer = new ConcurrentLinkedDeque<>();
    private final AtomicBoolean flushing = new AtomicBoolean(false);
    private final ObjectProvider<MeterRegistry> meterRegistryProvider;

    public ConfigChangePublisher(
            RabbitTemplate rabbitTemplate,
            ObjectMapper mapper,
            String actor,
            ObjectProvider<MeterRegistry> meterRegistryProvider) {
        this(rabbitTemplate, mapper, actor, meterRegistryProvider, DEFAULT_RETRY_BUFFER_SIZE);
    }

    public ConfigChangePublisher(
            RabbitTemplate rabbitTemplate,
            ObjectMapper mapper,
            String actor,
            ObjectProvider<MeterRegistry> meterRegistryProvider,
            int retryBufferMaxSize) {
        this.rabbitTemplate = rabbitTemplate;
        this.mapper = mapper;
        this.actor = actor;
        this.meterRegistryProvider = meterRegistryProvider;
        this.retryBufferMaxSize = retryBufferMaxSize <= 0 ? DEFAULT_RETRY_BUFFER_SIZE : retryBufferMaxSize;
    }

    /** Single-entity convenience. */
    public void publishSingle(String entityType, String entityId, ChangeType changeType) {
        publish(new ConfigChangedEvent(
                entityType, List.of(entityId), changeType, Instant.now(), actor, null));
    }

    /** Bulk wildcard convenience — invalidates the whole entity-type cache. */
    public void publishBulk(String entityType, ChangeType changeType) {
        publish(new ConfigChangedEvent(
                entityType, List.of(), changeType, Instant.now(), actor, null));
    }

    /** Targeted multi-entity convenience. */
    public void publishMany(String entityType, List<String> entityIds, ChangeType changeType) {
        publish(new ConfigChangedEvent(
                entityType, entityIds, changeType, Instant.now(), actor, null));
    }

    /** Direct publish — caller controls every field including {@code traceparent}. */
    public void publish(ConfigChangedEvent event) {
        if (!trySend(event)) {
            enqueueForRetry(event);
        }
    }

    /**
     * Periodic drain of the retry buffer. Scheduled at the
     * {@code igc.platform.config.publisher.retry.flush-interval} interval
     * (default 30 s). Stops on first AMQP failure so a stuck broker
     * doesn't burn through the whole buffer in one tick.
     */
    @Scheduled(fixedDelayString = "${igc.platform.config.publisher.retry.flush-interval:PT30S}",
            initialDelayString = "${igc.platform.config.publisher.retry.initial-delay:PT30S}")
    public void flushRetryBuffer() {
        if (retryBuffer.isEmpty()) {
            return;
        }
        if (!flushing.compareAndSet(false, true)) {
            return;
        }
        try {
            int flushed = 0;
            while (true) {
                ConfigChangedEvent next = retryBuffer.peekFirst();
                if (next == null) {
                    break;
                }
                if (!trySend(next)) {
                    // Broker still unhappy — leave the head where it is and stop.
                    break;
                }
                retryBuffer.pollFirst();
                flushed++;
            }
            if (flushed > 0) {
                recordCounter("config.change.retry.flushed", flushed);
                int remaining = retryBuffer.size();
                if (remaining > 0) {
                    log.info("config publisher: retry flush sent {} buffered event(s); {} remaining",
                            flushed, remaining);
                } else {
                    log.info("config publisher: retry flush drained {} buffered event(s); buffer empty",
                            flushed);
                }
            }
        } finally {
            flushing.set(false);
        }
    }

    /** Visible for testing + metrics. */
    public int retryBufferSize() {
        return retryBuffer.size();
    }

    /**
     * Try to send {@code event} to the broker.
     *
     * @return {@code true} if the broker accepted the message; {@code false}
     *         if AMQP raised. Serialisation failures are not retryable and
     *         return {@code true} (drop, don't enqueue).
     */
    private boolean trySend(ConfigChangedEvent event) {
        byte[] body;
        try {
            body = mapper.writeValueAsBytes(event);
        } catch (JsonProcessingException e) {
            // Serialisation failures are deterministic — retrying won't help.
            // Drop and increment the dropped counter.
            log.warn("config publisher: serialisation failed for entityType={}, dropping event ({})",
                    event.entityType(), e.getOriginalMessage());
            recordCounter("config.change.retry.dropped", 1);
            return true;
        }
        try {
            rabbitTemplate.send(EXCHANGE, ROUTING_KEY, MessageBuilder
                    .withBody(body)
                    .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                    .setContentEncoding("utf-8")
                    .build());
            log.debug("config publisher: published entityType={} ids={} changeType={}",
                    event.entityType(), event.entityIds(), event.changeType());
            return true;
        } catch (AmqpException e) {
            log.warn("config publisher: AMQP publish failed for entityType={} — buffering for retry ({})",
                    event.entityType(), e.getMessage());
            return false;
        }
    }

    private void enqueueForRetry(ConfigChangedEvent event) {
        // Drop oldest if the buffer is full.
        while (retryBuffer.size() >= retryBufferMaxSize) {
            ConfigChangedEvent dropped = retryBuffer.pollFirst();
            if (dropped == null) {
                break;
            }
            recordCounter("config.change.retry.dropped", 1);
            log.warn("config publisher: retry buffer at cap ({}); dropped oldest event entityType={}",
                    retryBufferMaxSize, dropped.entityType());
        }
        retryBuffer.offerLast(event);
        recordCounter("config.change.retry.enqueued", 1);
    }

    private void recordCounter(String name, long delta) {
        if (delta <= 0) return;
        MeterRegistry registry = meterRegistryProvider.getIfAvailable();
        if (registry == null) return;
        Counter.builder(name)
                .description("Config-change publisher retry buffer activity")
                .tags(Tags.empty())
                .register(registry)
                .increment(delta);
    }
}
