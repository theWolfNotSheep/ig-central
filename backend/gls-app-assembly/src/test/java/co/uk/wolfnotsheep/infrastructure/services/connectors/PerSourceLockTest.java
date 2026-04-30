package co.uk.wolfnotsheep.infrastructure.services.connectors;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PerSourceLockTest {

    private LockProvider provider;
    private SimpleLock lock;
    private MeterRegistry meterRegistry;
    private PerSourceLock helper;

    @BeforeEach
    void setUp() {
        provider = mock(LockProvider.class);
        lock = mock(SimpleLock.class);
        meterRegistry = new SimpleMeterRegistry();
        helper = new PerSourceLock(providerOf(provider), meterRegistryProviderOf(meterRegistry));
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<LockProvider> providerOf(LockProvider lp) {
        ObjectProvider<LockProvider> p = mock(ObjectProvider.class);
        when(p.getIfAvailable()).thenReturn(lp);
        return p;
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<MeterRegistry> meterRegistryProviderOf(MeterRegistry mr) {
        ObjectProvider<MeterRegistry> p = mock(ObjectProvider.class);
        when(p.getIfAvailable()).thenReturn(mr);
        return p;
    }

    private double counter(String name, String source) {
        return meterRegistry.find(name).tag("source", source).counter() == null
                ? 0.0
                : meterRegistry.find(name).tag("source", source).counter().count();
    }

    @Test
    void lock_acquired_runs_action_and_releases() {
        when(provider.lock(any(LockConfiguration.class))).thenReturn(Optional.of(lock));
        AtomicBoolean ran = new AtomicBoolean(false);

        boolean result = helper.withLock("drive-poll-1", Duration.ofMinutes(5),
                () -> ran.set(true));

        assertThat(result).isTrue();
        assertThat(ran).isTrue();
        verify(lock, times(1)).unlock();
        assertThat(counter("connector.lock.acquired", "drive")).isEqualTo(1.0);
        assertThat(counter("connector.lock.skipped", "drive")).isEqualTo(0.0);
    }

    @Test
    void lock_held_elsewhere_skips_action() {
        when(provider.lock(any(LockConfiguration.class))).thenReturn(Optional.empty());
        AtomicBoolean ran = new AtomicBoolean(false);

        boolean result = helper.withLock("drive-poll-1", Duration.ofMinutes(5),
                () -> ran.set(true));

        assertThat(result).isFalse();
        assertThat(ran).isFalse();
        verify(lock, never()).unlock();
        assertThat(counter("connector.lock.acquired", "drive")).isEqualTo(0.0);
        assertThat(counter("connector.lock.skipped", "drive")).isEqualTo(1.0);
    }

    @Test
    void lock_unlocked_even_when_action_throws() {
        when(provider.lock(any(LockConfiguration.class))).thenReturn(Optional.of(lock));

        try {
            helper.withLock("drive-poll-1", Duration.ofMinutes(5),
                    () -> { throw new RuntimeException("boom"); });
        } catch (RuntimeException ignored) {
            // expected
        }

        verify(lock, times(1)).unlock();
        // Acquired counted once even though the action threw — the lock was held.
        assertThat(counter("connector.lock.acquired", "drive")).isEqualTo(1.0);
    }

    @Test
    void absent_LockProvider_falls_through_to_action_and_counts_acquired() {
        PerSourceLock noProvider = new PerSourceLock(
                providerOf(null), meterRegistryProviderOf(meterRegistry));
        AtomicBoolean ran = new AtomicBoolean(false);

        boolean result = noProvider.withLock("drive-poll-1", Duration.ofMinutes(5),
                () -> ran.set(true));

        assertThat(result).isTrue();
        assertThat(ran).isTrue();
        // Single-replica fall-through still counts as acquired so the dashboard
        // reflects that the work happened.
        assertThat(counter("connector.lock.acquired", "drive")).isEqualTo(1.0);
    }

    @Test
    void lock_configuration_uses_lock_name_and_lockAtMostFor() {
        when(provider.lock(any(LockConfiguration.class))).thenReturn(Optional.of(lock));

        helper.withLock("gmail-poll-pipeline-x-node-y", Duration.ofMinutes(7), () -> {});

        ArgumentCaptor<LockConfiguration> cfg = ArgumentCaptor.forClass(LockConfiguration.class);
        verify(provider, times(1)).lock(cfg.capture());
        assertThat(cfg.getValue().getName()).isEqualTo("gmail-poll-pipeline-x-node-y");
        assertThat(cfg.getValue().getLockAtMostFor()).isEqualTo(Duration.ofMinutes(7));
        // Source is the prefix before the first dash.
        assertThat(counter("connector.lock.acquired", "gmail")).isEqualTo(1.0);
    }

    @Test
    void absent_MeterRegistry_does_not_break_lock_acquisition() {
        PerSourceLock noMetrics = new PerSourceLock(
                providerOf(provider), meterRegistryProviderOf(null));
        when(provider.lock(any(LockConfiguration.class))).thenReturn(Optional.of(lock));
        AtomicBoolean ran = new AtomicBoolean(false);

        boolean result = noMetrics.withLock("drive-poll-1", Duration.ofMinutes(5),
                () -> ran.set(true));

        assertThat(result).isTrue();
        assertThat(ran).isTrue();
    }

    @Test
    void source_extraction_falls_back_to_unknown_for_unconventional_names() {
        assertThat(PerSourceLock.sourceOf(null)).isEqualTo("unknown");
        assertThat(PerSourceLock.sourceOf("")).isEqualTo("unknown");
        assertThat(PerSourceLock.sourceOf("nodash")).isEqualTo("unknown");
        assertThat(PerSourceLock.sourceOf("-leading")).isEqualTo("unknown");
        assertThat(PerSourceLock.sourceOf("drive-poll-abc")).isEqualTo("drive");
        assertThat(PerSourceLock.sourceOf("gmail-poll-x-y")).isEqualTo("gmail");
    }
}
