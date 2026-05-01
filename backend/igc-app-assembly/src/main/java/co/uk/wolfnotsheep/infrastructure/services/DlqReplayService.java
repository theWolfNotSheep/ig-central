package co.uk.wolfnotsheep.infrastructure.services;

import com.rabbitmq.client.GetResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
 * is {@code igc.documents.dlq} and {@code igc.pipeline.dlq}.
 *
 * <p>Phase 2.6 — supports {@code dryRun=true} mode that pops messages
 * via {@code basicGet} + {@code basicNack(requeue=true)} so operators
 * can preview what would be replayed without actually re-publishing.
 * Plus per-queue ShedLock idempotency so two concurrent admin calls
 * on the same DLQ don't compete.
 */
@Service
public class DlqReplayService {

    private static final Logger log = LoggerFactory.getLogger(DlqReplayService.class);
    private static final Set<String> DEFAULT_ALLOWED_DLQS = Set.of(
            "igc.documents.dlq", "igc.pipeline.dlq");

    private final RabbitTemplate rabbitTemplate;
    private final ObjectProvider<MeterRegistry> meterRegistryProvider;
    private final ObjectProvider<LockProvider> lockProviderProvider;
    private final Set<String> allowedDlqs;

    public DlqReplayService(RabbitTemplate rabbitTemplate,
                            ObjectProvider<MeterRegistry> meterRegistryProvider,
                            ObjectProvider<LockProvider> lockProviderProvider) {
        this(rabbitTemplate, meterRegistryProvider, lockProviderProvider, DEFAULT_ALLOWED_DLQS);
    }

    /** Visible for testing — override the allowed-DLQ set. */
    DlqReplayService(RabbitTemplate rabbitTemplate,
                     ObjectProvider<MeterRegistry> meterRegistryProvider,
                     ObjectProvider<LockProvider> lockProviderProvider,
                     Set<String> allowedDlqs) {
        this.rabbitTemplate = rabbitTemplate;
        this.meterRegistryProvider = meterRegistryProvider;
        this.lockProviderProvider = lockProviderProvider;
        this.allowedDlqs = allowedDlqs;
    }

    /** Real replay — re-publishes and acks. */
    public ReplayResult replay(String queueName, int maxMessages) {
        return runReplay(queueName, maxMessages, /* dryRun */ false);
    }

    /** Dry-run replay — peeks via basicGet + nack-requeue; nothing changes. */
    public ReplayResult dryRun(String queueName, int maxMessages) {
        return runReplay(queueName, maxMessages, /* dryRun */ true);
    }

    private ReplayResult runReplay(String queueName, int maxMessages, boolean dryRun) {
        if (!allowedDlqs.contains(queueName)) {
            throw new IllegalArgumentException(
                    "queue " + queueName + " is not in the allowed DLQ replay list " + allowedDlqs);
        }
        if (maxMessages <= 0) {
            throw new IllegalArgumentException("maxMessages must be > 0");
        }

        // Per-queue idempotency: only one admin can drain a given DLQ at a time.
        // Skip lock when no LockProvider — single-replica deployments without ShedLock.
        LockProvider provider = lockProviderProvider == null ? null : lockProviderProvider.getIfAvailable();
        if (provider != null) {
            String lockName = "dlq-replay-" + queueName;
            LockConfiguration cfg = new LockConfiguration(
                    Instant.now(), lockName, Duration.ofMinutes(5), Duration.ZERO);
            Optional<SimpleLock> lock = provider.lock(cfg);
            if (lock.isEmpty()) {
                log.info("DLQ replay rejected — another caller is draining {} (lock held)", queueName);
                throw new ReplayInProgressException(queueName);
            }
            try {
                return doReplay(queueName, maxMessages, dryRun);
            } finally {
                lock.get().unlock();
            }
        }
        return doReplay(queueName, maxMessages, dryRun);
    }

    private enum StepOutcome { EMPTY, REPLAYED, SKIPPED }

    private ReplayResult doReplay(String queueName, int maxMessages, boolean dryRun) {
        int replayed = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();
        List<ReplayPreview> preview = dryRun ? new ArrayList<>() : null;

        for (int i = 0; i < maxMessages; i++) {
            StepOutcome step = rabbitTemplate.<StepOutcome>execute(channel -> {
                GetResponse resp = channel.basicGet(queueName, /* autoAck */ false);
                if (resp == null) return StepOutcome.EMPTY;
                long deliveryTag = resp.getEnvelope().getDeliveryTag();
                StepOutcome outcome;
                try {
                    Message message = toMessage(resp);
                    Optional<MessageOrigin> origin = extractOrigin(message);
                    boolean acted;
                    if (dryRun) {
                        if (origin.isPresent()) {
                            preview.add(new ReplayPreview(
                                    origin.get().exchange(), origin.get().routingKey(),
                                    extractReason(message), bodySize(message)));
                            acted = true;
                        } else {
                            acted = false;
                        }
                    } else {
                        acted = origin.isPresent() && publishToOrigin(message, origin.get());
                    }
                    outcome = acted ? StepOutcome.REPLAYED : StepOutcome.SKIPPED;
                } catch (Exception e) {
                    log.error("DLQ replay: failed processing message from {}: {}",
                            queueName, e.getMessage(), e);
                    errors.add(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
                    outcome = StepOutcome.SKIPPED;
                }
                // Dry-run: requeue so messages stay in DLQ. Real-run: ack.
                if (dryRun) {
                    channel.basicNack(deliveryTag, false, /* requeue */ true);
                } else {
                    channel.basicAck(deliveryTag, false);
                }
                return outcome;
            });
            if (step == null || step == StepOutcome.EMPTY) break;
            if (step == StepOutcome.REPLAYED) replayed++;
            else skipped++;
        }

        recordCounter(queueName, dryRun, "replayed", replayed);
        recordCounter(queueName, dryRun, "skipped", skipped);
        log.info("DLQ replay complete: queue={} dryRun={} replayed={} skipped={} errors={}",
                queueName, dryRun, replayed, skipped, errors.size());
        return new ReplayResult(queueName, dryRun, replayed, skipped, errors,
                preview == null ? List.of() : preview);
    }

    /** @return {@code true} if the message was re-published; {@code false} if missing-x-death etc. */
    private boolean publishToOrigin(Message message, MessageOrigin origin) {
        MessageProperties props = message.getMessageProperties();
        MessageProperties replayProps = copyProperties(props);
        replayProps.getHeaders().remove("x-death");
        replayProps.getHeaders().remove("x-first-death-exchange");
        replayProps.getHeaders().remove("x-first-death-queue");
        replayProps.getHeaders().remove("x-first-death-reason");

        Message replay = MessageBuilder.fromMessage(new Message(message.getBody(), replayProps)).build();
        rabbitTemplate.send(origin.exchange(), origin.routingKey(), replay);
        log.info("DLQ replay: re-published message to {}/{}", origin.exchange(), origin.routingKey());
        return true;
    }

    private static Optional<MessageOrigin> extractOrigin(Message message) {
        MessageProperties props = message.getMessageProperties();
        if (props == null || props.getHeaders() == null) return Optional.empty();
        Object xDeath = props.getHeaders().get("x-death");
        if (!(xDeath instanceof List<?>) || ((List<?>) xDeath).isEmpty()) {
            return Optional.empty();
        }
        Object firstEntry = ((List<?>) xDeath).get(0);
        if (!(firstEntry instanceof Map<?, ?>)) return Optional.empty();
        Map<?, ?> origin = (Map<?, ?>) firstEntry;
        String exchange = stringOrNull(origin.get("exchange"));
        Object routingKeysObj = origin.get("routing-keys");
        String routingKey = null;
        if (routingKeysObj instanceof List<?> rks && !rks.isEmpty()) {
            routingKey = stringOrNull(rks.get(0));
        }
        if (exchange == null || routingKey == null) return Optional.empty();
        return Optional.of(new MessageOrigin(exchange, routingKey));
    }

    private static String extractReason(Message message) {
        MessageProperties props = message.getMessageProperties();
        if (props == null || props.getHeaders() == null) return null;
        Object first = props.getHeaders().get("x-first-death-reason");
        if (first != null) return first.toString();
        Object xDeath = props.getHeaders().get("x-death");
        if (xDeath instanceof List<?> list && !list.isEmpty()
                && list.get(0) instanceof Map<?, ?> m) {
            Object reason = m.get("reason");
            return reason == null ? null : reason.toString();
        }
        return null;
    }

    private static int bodySize(Message message) {
        return message.getBody() == null ? 0 : message.getBody().length;
    }

    private static Message toMessage(GetResponse resp) {
        MessageProperties props = new MessageProperties();
        if (resp.getProps() != null) {
            if (resp.getProps().getContentType() != null) {
                props.setContentType(resp.getProps().getContentType());
            }
            if (resp.getProps().getHeaders() != null) {
                resp.getProps().getHeaders().forEach((k, v) -> props.setHeader(k, v));
            }
        }
        return new Message(resp.getBody(), props);
    }

    private static MessageProperties copyProperties(MessageProperties source) {
        MessageProperties dest = new MessageProperties();
        if (source == null) return dest;
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

    private void recordCounter(String queue, boolean dryRun, String outcome, int delta) {
        if (delta <= 0) return;
        MeterRegistry registry = meterRegistryProvider.getIfAvailable();
        if (registry == null) return;
        Counter.builder("dlq.replay")
                .description("Count of DLQ messages by replay outcome")
                .tags(Tags.of("queue", queue, "outcome", outcome,
                        "mode", dryRun ? "dry_run" : "real"))
                .register(registry)
                .increment(delta);
    }

    public record MessageOrigin(String exchange, String routingKey) {}

    public record ReplayPreview(String originExchange, String originRoutingKey,
                                String reason, int bodyBytes) {}

    public record ReplayResult(String queue, boolean dryRun, int replayed, int skipped,
                               List<String> errors, List<ReplayPreview> preview) {}

    /** Thrown when another caller already holds the per-queue replay lock. */
    public static class ReplayInProgressException extends RuntimeException {
        public ReplayInProgressException(String queue) {
            super("DLQ replay already in progress for queue " + queue);
        }
    }
}
