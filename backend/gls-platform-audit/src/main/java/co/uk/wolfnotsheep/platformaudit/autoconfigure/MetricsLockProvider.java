package co.uk.wolfnotsheep.platformaudit.autoconfigure;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Decorator that wraps any {@link LockProvider} with a Micrometer
 * counter on every {@code lock()} attempt, tagged by lock {@code name}
 * and {@code outcome} ({@code acquired} or {@code skipped}).
 *
 * <p>Lets operators see leader-election dynamics — how often a given
 * scheduled task's tick was held by another replica vs. acquired
 * locally. A high skip rate on a single lock name means a busy leader
 * (expected); equal counts across replicas means even leadership
 * rotation; a single replica acquiring 100% means the others are
 * never trying (or always losing the race).
 *
 * <p>Counter: {@code scheduler.lock{name, outcome}}.
 */
class MetricsLockProvider implements LockProvider {

    private static final Logger log = LoggerFactory.getLogger(MetricsLockProvider.class);

    private final LockProvider delegate;
    private final MeterRegistry meterRegistry;

    MetricsLockProvider(LockProvider delegate, MeterRegistry meterRegistry) {
        this.delegate = delegate;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public Optional<SimpleLock> lock(LockConfiguration lockConfiguration) {
        Optional<SimpleLock> result = delegate.lock(lockConfiguration);
        String outcome = result.isPresent() ? "acquired" : "skipped";
        if (meterRegistry != null) {
            Counter.builder("scheduler.lock")
                    .description("ShedLock lock acquisition attempts by name and outcome")
                    .tags(Tags.of(
                            "name", lockConfiguration.getName(),
                            "outcome", outcome))
                    .register(meterRegistry)
                    .increment();
        }
        log.trace("scheduler lock attempt: name={} outcome={}",
                lockConfiguration.getName(), outcome);
        return result;
    }
}
