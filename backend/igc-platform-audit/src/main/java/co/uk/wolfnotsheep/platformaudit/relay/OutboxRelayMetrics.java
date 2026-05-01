package co.uk.wolfnotsheep.platformaudit.relay;

import co.uk.wolfnotsheep.platformaudit.envelope.Tier;
import co.uk.wolfnotsheep.platformaudit.outbox.AuditOutboxRepository;
import co.uk.wolfnotsheep.platformaudit.outbox.OutboxStatus;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;

import java.time.Instant;

/**
 * Cohesive home for {@link OutboxRelay}'s Micrometer instruments.
 *
 * <p>Counters + Timer are tagged by {@code tier} (DOMAIN / SYSTEM) and
 * {@code outcome} (published / retried / failed). A separate Gauge
 * tracks the current PENDING-row depth — useful for queue-backlog
 * dashboards and the alert "are we keeping up with the producers?".
 *
 * <p>Cardinality is bounded:
 *
 * <ul>
 *     <li>{@code tier}: 2 values (DOMAIN / SYSTEM).</li>
 *     <li>{@code outcome}: 3 values (published / retried / failed).</li>
 * </ul>
 *
 * <p>The repository ref drives the gauge: {@link io.micrometer.core.instrument.Gauge}
 * polls {@link OutboxRelayMetrics#pendingDepth} on each scrape.
 * Cheap query — backed by {@code idx_status_nextRetry}.
 */
public class OutboxRelayMetrics {

    private static final String PUBLISH_RESULT = "igc_audit_relay_publish_total";
    private static final String PUBLISH_DURATION = "igc_audit_relay_publish_duration_seconds";
    private static final String QUEUE_DEPTH = "igc_audit_relay_pending_depth";

    private final MeterRegistry registry;
    private final AuditOutboxRepository repository;

    public OutboxRelayMetrics(MeterRegistry registry, AuditOutboxRepository repository) {
        this.registry = registry;
        this.repository = repository;
        // Register the depth gauge once at construction. Subsequent
        // scrapes call pendingDepth() and the value flows automatically.
        io.micrometer.core.instrument.Gauge.builder(QUEUE_DEPTH, this, OutboxRelayMetrics::pendingDepth)
                .description("Number of audit_outbox rows in PENDING status (relay backlog).")
                .register(registry);
    }

    public Timer.Sample startTimer() {
        return Timer.start(registry);
    }

    public void recordPublished(Timer.Sample sample, Tier tier) {
        Tags tags = Tags.of(Tag.of("tier", tier.name()), Tag.of("outcome", "published"));
        sample.stop(registry.timer(PUBLISH_DURATION, tags));
        registry.counter(PUBLISH_RESULT, tags).increment();
    }

    public void recordRetry(Timer.Sample sample, Tier tier) {
        Tags tags = Tags.of(Tag.of("tier", tier.name()), Tag.of("outcome", "retried"));
        sample.stop(registry.timer(PUBLISH_DURATION, tags));
        registry.counter(PUBLISH_RESULT, tags).increment();
    }

    public void recordFailed(Timer.Sample sample, Tier tier) {
        Tags tags = Tags.of(Tag.of("tier", tier.name()), Tag.of("outcome", "failed"));
        sample.stop(registry.timer(PUBLISH_DURATION, tags));
        registry.counter(PUBLISH_RESULT, tags).increment();
    }

    private double pendingDepth() {
        // Repository query is bounded by idx_status_nextRetry — cheap
        // even at high outbox volumes. Returns the count of rows that
        // would be picked up by the relay's next poll.
        try {
            return repository.findByStatusAndNextRetryAtBeforeOrderByCreatedAtAsc(
                    OutboxStatus.PENDING, Instant.now(),
                    org.springframework.data.domain.PageRequest.of(0, 10_000)).size();
        } catch (RuntimeException e) {
            return Double.NaN;
        }
    }
}
