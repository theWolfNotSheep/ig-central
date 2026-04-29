package co.uk.wolfnotsheep.bert.registry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Single-flight coordinator for {@code POST /v1/models/reload}. The
 * actual fetch + load logic lives in the DJL impl that ships with
 * Phase 1.4 PR4; this scaffolds the in-flight state-machine so the
 * controller already has the right shape.
 *
 * <p>State is in-memory + replica-local — a reload triggered on one
 * replica doesn't propagate. Each replica's load balancer drains
 * traffic from it during its own reload via the readiness probe.
 */
@Component
public class ReloadCoordinator {

    private static final Logger log = LoggerFactory.getLogger(ReloadCoordinator.class);

    private final AtomicReference<Instant> startedAt = new AtomicReference<>(null);

    /**
     * Mark the reload as started. Throws if one is already in flight.
     *
     * @return the start instant (echoed on the controller's 202).
     */
    public Instant beginReload() {
        Instant now = Instant.now();
        if (!startedAt.compareAndSet(null, now)) {
            throw new ReloadInProgressException(
                    "reload already in progress (started at " + startedAt.get() + ")");
        }
        log.info("bert: reload started at {}", now);
        return now;
    }

    public void endReload() {
        Instant prev = startedAt.getAndSet(null);
        if (prev != null) {
            log.info("bert: reload completed (took {}ms)",
                    java.time.Duration.between(prev, Instant.now()).toMillis());
        }
    }

    public boolean isInProgress() {
        return startedAt.get() != null;
    }
}
