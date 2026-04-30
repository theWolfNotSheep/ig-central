package co.uk.wolfnotsheep.platformaudit.autoconfigure;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MetricsLockProviderTest {

    @Test
    void acquired_lock_increments_acquired_counter() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        LockProvider delegate = mock(LockProvider.class);
        SimpleLock lock = mock(SimpleLock.class);
        when(delegate.lock(any(LockConfiguration.class))).thenReturn(Optional.of(lock));

        MetricsLockProvider provider = new MetricsLockProvider(delegate, registry);
        Optional<SimpleLock> result = provider.lock(new LockConfiguration(
                Instant.now(), "test-lock", Duration.ofSeconds(30), Duration.ZERO));

        assertThat(result).isPresent();
        assertThat(registry.get("scheduler.lock")
                .tags("name", "test-lock", "outcome", "acquired").counter().count())
                .isEqualTo(1.0);
    }

    @Test
    void unavailable_lock_increments_skipped_counter() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        LockProvider delegate = mock(LockProvider.class);
        when(delegate.lock(any(LockConfiguration.class))).thenReturn(Optional.empty());

        MetricsLockProvider provider = new MetricsLockProvider(delegate, registry);
        Optional<SimpleLock> result = provider.lock(new LockConfiguration(
                Instant.now(), "another-lock", Duration.ofSeconds(30), Duration.ZERO));

        assertThat(result).isEmpty();
        assertThat(registry.get("scheduler.lock")
                .tags("name", "another-lock", "outcome", "skipped").counter().count())
                .isEqualTo(1.0);
    }

    @Test
    void counters_segregated_by_lock_name() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        LockProvider delegate = mock(LockProvider.class);
        SimpleLock lock = mock(SimpleLock.class);
        when(delegate.lock(any(LockConfiguration.class))).thenReturn(Optional.of(lock));

        MetricsLockProvider provider = new MetricsLockProvider(delegate, registry);
        provider.lock(new LockConfiguration(Instant.now(), "lock-a", Duration.ofSeconds(30), Duration.ZERO));
        provider.lock(new LockConfiguration(Instant.now(), "lock-b", Duration.ofSeconds(30), Duration.ZERO));
        provider.lock(new LockConfiguration(Instant.now(), "lock-a", Duration.ofSeconds(30), Duration.ZERO));

        assertThat(registry.get("scheduler.lock")
                .tags("name", "lock-a", "outcome", "acquired").counter().count())
                .isEqualTo(2.0);
        assertThat(registry.get("scheduler.lock")
                .tags("name", "lock-b", "outcome", "acquired").counter().count())
                .isEqualTo(1.0);
    }

    @Test
    void null_meter_registry_does_not_break() {
        LockProvider delegate = mock(LockProvider.class);
        SimpleLock lock = mock(SimpleLock.class);
        when(delegate.lock(any(LockConfiguration.class))).thenReturn(Optional.of(lock));

        MetricsLockProvider provider = new MetricsLockProvider(delegate, null);
        Optional<SimpleLock> result = provider.lock(new LockConfiguration(
                Instant.now(), "test-lock", Duration.ofSeconds(30), Duration.ZERO));

        assertThat(result).isPresent();
    }
}
