package co.uk.wolfnotsheep.infrastructure.services.connectors;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Per-source ShedLock helper for connector pollers (Phase 1.13).
 * Each connected drive / mailbox / external source gets its own lock
 * key (e.g. {@code "drive-poll-<driveId>"}, {@code "gmail-poll-<userId>"})
 * so multiple replicas can process different sources concurrently
 * but never the same source.
 *
 * <p>Differs from method-level {@code @SchedulerLock} (which gates the
 * entire scheduled method): here we lock per-iteration so the
 * scheduled method runs on every replica, and each replica picks up
 * whichever sources are currently unlocked.
 *
 * <p>{@link LockProvider} is auto-configured by
 * {@code gls-platform-audit}'s {@code AuditRelayLockConfig}. The
 * provider is injected via {@link ObjectProvider} so a deployment
 * without {@code shedlock-provider-mongo} on the classpath
 * continues to work in single-replica mode (every {@code withLock}
 * call falls through to the action).
 *
 * <p>Emits Micrometer counters {@code connector.lock.acquired} and
 * {@code connector.lock.skipped} tagged by {@code source} (derived
 * from the lock-name prefix — {@code drive-poll-...} → {@code drive},
 * {@code gmail-poll-...} → {@code gmail}). Source cardinality is
 * bounded; the lock-name itself is high-cardinality and intentionally
 * not tagged.
 */
@Component
public class PerSourceLock {

    private static final Logger log = LoggerFactory.getLogger(PerSourceLock.class);
    private static final String UNKNOWN_SOURCE = "unknown";

    private final ObjectProvider<LockProvider> lockProviderProvider;
    private final ObjectProvider<MeterRegistry> meterRegistryProvider;

    public PerSourceLock(
            ObjectProvider<LockProvider> lockProviderProvider,
            ObjectProvider<MeterRegistry> meterRegistryProvider) {
        this.lockProviderProvider = lockProviderProvider;
        this.meterRegistryProvider = meterRegistryProvider;
    }

    /**
     * Run {@code action} only if the lock for {@code lockName} can be
     * acquired. Returns {@code true} if the action ran, {@code false}
     * if another replica holds the lock (silently skipped).
     *
     * @param lockName        Unique per-source key (e.g.
     *                        {@code "drive-poll-<driveId>"}).
     * @param lockAtMostFor   Hard cap on lock duration — released
     *                        forcibly after this even if the action
     *                        is still running. Pick a value larger
     *                        than the worst-case action runtime.
     * @param action          The work to do under the lock.
     */
    public boolean withLock(String lockName, Duration lockAtMostFor, Runnable action) {
        String source = sourceOf(lockName);
        LockProvider provider = lockProviderProvider.getIfAvailable();
        if (provider == null) {
            // Single-replica deployment without ShedLock — just run the action.
            // Count as "acquired" so dashboards reflect that the work happened.
            recordAcquired(source);
            action.run();
            return true;
        }
        LockConfiguration cfg = new LockConfiguration(
                Instant.now(), lockName, lockAtMostFor, Duration.ZERO);
        Optional<SimpleLock> lock = provider.lock(cfg);
        if (lock.isEmpty()) {
            recordSkipped(source);
            log.debug("per-source lock {} held by another replica — skipping", lockName);
            return false;
        }
        recordAcquired(source);
        try {
            action.run();
            return true;
        } finally {
            lock.get().unlock();
        }
    }

    /**
     * Extract the source tag from a lock-name prefix. {@code drive-poll-abc}
     * → {@code drive}, {@code gmail-poll-x-y} → {@code gmail}. Falls back
     * to {@code "unknown"} for names that don't match the convention.
     */
    static String sourceOf(String lockName) {
        if (lockName == null || lockName.isEmpty()) {
            return UNKNOWN_SOURCE;
        }
        int dash = lockName.indexOf('-');
        if (dash <= 0) {
            return UNKNOWN_SOURCE;
        }
        return lockName.substring(0, dash);
    }

    private void recordAcquired(String source) {
        MeterRegistry registry = meterRegistryProvider.getIfAvailable();
        if (registry == null) {
            return;
        }
        Counter.builder("connector.lock.acquired")
                .description("Per-source connector poll lock acquisitions")
                .tags(Tags.of("source", source))
                .register(registry)
                .increment();
    }

    private void recordSkipped(String source) {
        MeterRegistry registry = meterRegistryProvider.getIfAvailable();
        if (registry == null) {
            return;
        }
        Counter.builder("connector.lock.skipped")
                .description("Per-source connector poll lock acquisitions skipped because another replica holds the lock")
                .tags(Tags.of("source", source))
                .register(registry)
                .increment();
    }
}
