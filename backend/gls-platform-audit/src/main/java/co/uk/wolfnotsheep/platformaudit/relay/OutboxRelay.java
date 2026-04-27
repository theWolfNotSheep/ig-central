package co.uk.wolfnotsheep.platformaudit.relay;

import co.uk.wolfnotsheep.platformaudit.envelope.AuditEvent;
import co.uk.wolfnotsheep.platformaudit.envelope.Tier;
import co.uk.wolfnotsheep.platformaudit.outbox.AuditOutboxRecord;
import co.uk.wolfnotsheep.platformaudit.outbox.AuditOutboxRepository;
import co.uk.wolfnotsheep.platformaudit.outbox.OutboxStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Polls {@code audit_outbox} for {@code PENDING} rows and publishes each
 * envelope to its tier-specific routing key on the configured topic
 * exchange.
 *
 * <p>For each row in a poll cycle:
 *
 * <ol>
 *     <li>Read {@code envelope}, {@code tier}, {@code eventType} off the row.</li>
 *     <li>If {@code tier=DOMAIN}, transform via
 *         {@link Tier1HashTransformer} — strip raw {@code details.content}
 *         to {@code sha256:<hex>} hashes per CSV #6. Tier 2 envelopes pass
 *         through unchanged.</li>
 *     <li>Serialise to JSON and publish to {@code audit.tier1.<eventType>}
 *         (DOMAIN) or {@code audit.tier2.<eventType>} (SYSTEM) on the
 *         configured exchange.</li>
 *     <li>Mark the row {@code PUBLISHED} on success.</li>
 *     <li>On failure: increment {@code attempts}, set {@code nextRetryAt}
 *         to now + exponential backoff, capture {@code lastError}. Once
 *         {@code attempts >= maxAttempts}, mark {@code FAILED} and stop
 *         retrying.</li>
 * </ol>
 *
 * <p><strong>Single-writer constraint (CSV #4) is NOT enforced here.</strong>
 * For Tier 1 events, multi-replica deployments require a leader-election
 * shim (ShedLock or equivalent) to guarantee only one process publishes a
 * given event. That hardening lands as a follow-up. Until then: run a
 * single replica, or accept the at-least-once delivery property the
 * downstream consumer ({@code gls-audit-collector}) already deduplicates
 * on via {@code eventId}.
 *
 * <p>Scheduling uses Spring's {@code @Scheduled}. Consumers must declare
 * {@code @EnableScheduling} on their application — {@code gls-app-assembly}
 * already does. Without it, the relay bean is constructed but never ticks.
 */
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final String ROUTING_PREFIX_TIER_1 = "audit.tier1.";
    private static final String ROUTING_PREFIX_TIER_2 = "audit.tier2.";

    private final AuditOutboxRepository repository;
    private final RabbitTemplate rabbitTemplate;
    private final OutboxRelayProperties properties;
    private final ObjectMapper mapper;

    public OutboxRelay(
            AuditOutboxRepository repository,
            RabbitTemplate rabbitTemplate,
            OutboxRelayProperties properties) {
        this.repository = repository;
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    @Scheduled(fixedDelayString = "${gls.platform.audit.relay.poll-interval:PT5S}")
    @SchedulerLock(name = "gls-audit-outbox-relay",
            lockAtMostFor = "${gls.platform.audit.relay.lock-at-most-for:PT5M}",
            lockAtLeastFor = "${gls.platform.audit.relay.lock-at-least-for:PT0S}")
    public void pollOnce() {
        if (!properties.enabled()) {
            return;
        }
        Instant now = Instant.now();
        List<AuditOutboxRecord> batch = repository
                .findByStatusAndNextRetryAtBeforeOrderByCreatedAtAsc(
                        OutboxStatus.PENDING, now,
                        PageRequest.of(0, properties.batchSize()));
        if (batch.isEmpty()) {
            return;
        }
        log.debug("audit relay: polling — {} PENDING rows up to {}", batch.size(), now);
        for (AuditOutboxRecord row : batch) {
            try {
                publish(row);
            } catch (RuntimeException e) {
                handleFailure(row, e);
            }
        }
    }

    private void publish(AuditOutboxRecord row) {
        AuditEvent envelope = row.tier() == Tier.DOMAIN
                ? Tier1HashTransformer.toTier1(row.envelope())
                : row.envelope();
        String routingKey = routingKeyFor(envelope);
        String body;
        try {
            body = mapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            // Serialisation failure is not retryable — the envelope is
            // structurally broken. Mark FAILED immediately so a human looks
            // at it; do not bump attempts uselessly.
            markFailed(row, "envelope serialisation failed: " + e.getOriginalMessage());
            return;
        }

        org.springframework.amqp.core.Message message = org.springframework.amqp.core.MessageBuilder
                .withBody(body.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setContentEncoding("utf-8")
                .setMessageId(envelope.eventId())
                .build();

        rabbitTemplate.send(properties.exchange(), routingKey, message);
        markPublished(row);
        log.debug("audit relay: published eventId={} → {}/{}",
                envelope.eventId(), properties.exchange(), routingKey);
    }

    private void handleFailure(AuditOutboxRecord row, Exception cause) {
        int attempts = row.attempts() + 1;
        String error = (cause instanceof AmqpException ? "AMQP: " : "")
                + cause.getClass().getSimpleName() + ": " + cause.getMessage();
        if (attempts >= properties.maxAttempts()) {
            markFailed(row, error + " (gave up after " + attempts + " attempts)");
            log.warn("audit relay: eventId={} marked FAILED after {} attempts ({})",
                    row.eventId(), attempts, error);
            return;
        }
        Instant nextRetry = Instant.now().plus(backoffFor(attempts));
        AuditOutboxRecord retried = withRetry(row, attempts, error, nextRetry);
        repository.save(retried);
        log.warn("audit relay: eventId={} attempt {} failed, retrying after {} ({})",
                row.eventId(), attempts, nextRetry, error);
    }

    private void markPublished(AuditOutboxRecord row) {
        repository.save(new AuditOutboxRecord(
                row.id(), row.eventId(), row.tier(), row.eventType(),
                row.envelope(), OutboxStatus.PUBLISHED, row.attempts(), null,
                row.createdAt(), Instant.now(), row.nextRetryAt()));
    }

    private void markFailed(AuditOutboxRecord row, String error) {
        repository.save(new AuditOutboxRecord(
                row.id(), row.eventId(), row.tier(), row.eventType(),
                row.envelope(), OutboxStatus.FAILED, row.attempts() + 1, error,
                row.createdAt(), null, row.nextRetryAt()));
    }

    private AuditOutboxRecord withRetry(AuditOutboxRecord row, int attempts, String error, Instant nextRetry) {
        return new AuditOutboxRecord(
                row.id(), row.eventId(), row.tier(), row.eventType(),
                row.envelope(), OutboxStatus.PENDING, attempts, error,
                row.createdAt(), null, nextRetry);
    }

    private Duration backoffFor(int attempts) {
        long baseMillis = properties.backoffBase().toMillis();
        long capMillis = properties.backoffMax().toMillis();
        long expMillis;
        try {
            // 2^(attempts-1) — overflow-safe via Math.multiplyExact.
            expMillis = Math.multiplyExact(baseMillis, 1L << Math.min(attempts - 1, 30));
        } catch (ArithmeticException e) {
            expMillis = capMillis;
        }
        return Duration.ofMillis(Math.min(expMillis, capMillis));
    }

    private static String routingKeyFor(AuditEvent envelope) {
        return (envelope.tier() == Tier.DOMAIN ? ROUTING_PREFIX_TIER_1 : ROUTING_PREFIX_TIER_2)
                + envelope.eventType();
    }
}
