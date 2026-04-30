package co.uk.wolfnotsheep.infrastructure.services.connectors;

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
 */
@Component
public class PerSourceLock {

    private static final Logger log = LoggerFactory.getLogger(PerSourceLock.class);

    private final ObjectProvider<LockProvider> lockProviderProvider;

    public PerSourceLock(ObjectProvider<LockProvider> lockProviderProvider) {
        this.lockProviderProvider = lockProviderProvider;
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
        LockProvider provider = lockProviderProvider.getIfAvailable();
        if (provider == null) {
            // Single-replica deployment without ShedLock — just run the action.
            action.run();
            return true;
        }
        LockConfiguration cfg = new LockConfiguration(
                Instant.now(), lockName, lockAtMostFor, Duration.ZERO);
        Optional<SimpleLock> lock = provider.lock(cfg);
        if (lock.isEmpty()) {
            log.debug("per-source lock {} held by another replica — skipping", lockName);
            return false;
        }
        try {
            action.run();
            return true;
        } finally {
            lock.get().unlock();
        }
    }
}
