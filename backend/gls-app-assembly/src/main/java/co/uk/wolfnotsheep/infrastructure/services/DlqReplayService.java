package co.uk.wolfnotsheep.infrastructure.services;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Phase 2.3 plan item 4 — DLQ replay data path.
 *
 * <p>Reads messages from a dead-letter queue and re-publishes them to
 * the original exchange + routing key recorded in RabbitMQ's
 * {@code x-death} header. Bounded by the requested replay count so an
 * operator can drain a backlog incrementally.
 *
 * <p>The {@code x-death} header is an array of objects, each describing
 * one dead-letter event (queue, exchange, routing-keys, count, time,
 * reason). The first entry is the most recent dead-letter event — the
 * one we want to undo. We re-publish to that entry's
 * {@code exchange} with the first entry from {@code routing-keys}.
 *
 * <p>Allowed source queues are config-gated to a known list so the
 * endpoint can't be used to drain arbitrary queues. The default list
 * is {@code gls.documents.dlq} and {@code gls.pipeline.dlq}.
 *
 * <p>This is a data-path-only endpoint (Phase 2.3); the Phase 3 admin
 * UI will wrap it.
 */
@Service
public class DlqReplayService {

    private static final Logger log = LoggerFactory.getLogger(DlqReplayService.class);
    private static final Set<String> DEFAULT_ALLOWED_DLQS = Set.of(
            "gls.documents.dlq", "gls.pipeline.dlq");

    private final RabbitTemplate rabbitTemplate;
    private final ObjectProvider<MeterRegistry> meterRegistryProvider;
    private final Set<String> allowedDlqs;

    public DlqReplayService(RabbitTemplate rabbitTemplate,
                            ObjectProvider<MeterRegistry> meterRegistryProvider) {
        this(rabbitTemplate, meterRegistryProvider, DEFAULT_ALLOWED_DLQS);
    }

    /** Visible for testing — override the allowed-DLQ set. */
    DlqReplayService(RabbitTemplate rabbitTemplate,
                     ObjectProvider<MeterRegistry> meterRegistryProvider,
                     Set<String> allowedDlqs) {
        this.rabbitTemplate = rabbitTemplate;
        this.meterRegistryProvider = meterRegistryProvider;
        this.allowedDlqs = allowedDlqs;
    }

    public ReplayResult replay(String queueName, int maxMessages) {
        if (!allowedDlqs.contains(queueName)) {
            throw new IllegalArgumentException(
                    "queue " + queueName + " is not in the allowed DLQ replay list " + allowedDlqs);
        }
        if (maxMessages <= 0) {
            throw new IllegalArgumentException("maxMessages must be > 0");
        }

        int replayed = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();
        for (int i = 0; i < maxMessages; i++) {
            Message message = rabbitTemplate.receive(queueName);
            if (message == null) break;
            try {
                if (replayMessage(message)) {
                    replayed++;
                } else {
                    skipped++;
                }
            } catch (RuntimeException e) {
                log.error("DLQ replay: failed to replay message from {}: {}", queueName, e.getMessage(), e);
                errors.add(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
                skipped++;
            }
        }
        recordCounter(queueName, "replayed", replayed);
        recordCounter(queueName, "skipped", skipped);
        log.info("DLQ replay complete: queue={} replayed={} skipped={} errors={}",
                queueName, replayed, skipped, errors.size());
        return new ReplayResult(queueName, replayed, skipped, errors);
    }

    /** @return {@code true} if the message was re-published; {@code false} if skipped. */
    private boolean replayMessage(Message message) {
        MessageProperties props = message.getMessageProperties();
        if (props == null) return false;
        Map<String, Object> headers = props.getHeaders();
        if (headers == null) return false;

        Object xDeath = headers.get("x-death");
        if (!(xDeath instanceof List<?>) || ((List<?>) xDeath).isEmpty()) {
            log.warn("DLQ replay: message has no x-death header — cannot determine origin; skipping");
            return false;
        }
        Object firstEntry = ((List<?>) xDeath).get(0);
        if (!(firstEntry instanceof Map<?, ?>)) {
            return false;
        }
        Map<?, ?> origin = (Map<?, ?>) firstEntry;
        String originExchange = stringOrNull(origin.get("exchange"));
        Object routingKeysObj = origin.get("routing-keys");
        String originRoutingKey = null;
        if (routingKeysObj instanceof List<?> rks && !rks.isEmpty()) {
            originRoutingKey = stringOrNull(rks.get(0));
        }
        if (originExchange == null || originRoutingKey == null) {
            log.warn("DLQ replay: x-death missing exchange / routing-keys; skipping");
            return false;
        }

        // Strip x-death so the broker's dead-letter machinery starts fresh
        // if the message dies again.
        MessageProperties replayProps = MessagePropertiesBuilderCopy(props);
        replayProps.getHeaders().remove("x-death");
        replayProps.getHeaders().remove("x-first-death-exchange");
        replayProps.getHeaders().remove("x-first-death-queue");
        replayProps.getHeaders().remove("x-first-death-reason");

        Message replay = MessageBuilder.fromMessage(new Message(message.getBody(), replayProps)).build();
        rabbitTemplate.send(originExchange, originRoutingKey, replay);
        log.info("DLQ replay: re-published message to {}/{}", originExchange, originRoutingKey);
        return true;
    }

    /**
     * Spring AMQP doesn't expose a clean copy method for
     * {@link MessageProperties}; we duplicate the relevant fields by
     * hand. Body is kept in the caller — only the metadata is copied.
     */
    private static MessageProperties MessagePropertiesBuilderCopy(MessageProperties source) {
        MessageProperties dest = new MessageProperties();
        dest.setContentType(source.getContentType());
        dest.setContentEncoding(source.getContentEncoding());
        dest.setMessageId(source.getMessageId());
        dest.setCorrelationId(source.getCorrelationId());
        dest.setReplyTo(source.getReplyTo());
        dest.setExpiration(source.getExpiration());
        dest.setPriority(source.getPriority());
        dest.setTimestamp(source.getTimestamp());
        dest.setType(source.getType());
        dest.setUserId(source.getUserId());
        dest.setAppId(source.getAppId());
        dest.setDeliveryMode(source.getDeliveryMode());
        if (source.getHeaders() != null) {
            for (Map.Entry<String, Object> e : source.getHeaders().entrySet()) {
                dest.setHeader(e.getKey(), e.getValue());
            }
        }
        return dest;
    }

    private static String stringOrNull(Object o) {
        return o == null ? null : o.toString();
    }

    private void recordCounter(String queue, String outcome, int delta) {
        if (delta <= 0) return;
        MeterRegistry registry = meterRegistryProvider.getIfAvailable();
        if (registry == null) return;
        Counter.builder("dlq.replay")
                .description("Count of DLQ messages by replay outcome")
                .tags(Tags.of("queue", queue, "outcome", outcome))
                .register(registry)
                .increment(delta);
    }

    public record ReplayResult(String queue, int replayed, int skipped, List<String> errors) {}
}
