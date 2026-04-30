package co.uk.wolfnotsheep.infrastructure.services.connectors;

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
    private PerSourceLock helper;

    @BeforeEach
    void setUp() {
        provider = mock(LockProvider.class);
        lock = mock(SimpleLock.class);
        helper = new PerSourceLock(providerOf(provider));
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<LockProvider> providerOf(LockProvider lp) {
        ObjectProvider<LockProvider> p = mock(ObjectProvider.class);
        when(p.getIfAvailable()).thenReturn(lp);
        return p;
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
    }

    @Test
    void absent_LockProvider_falls_through_to_action() {
        PerSourceLock noProvider = new PerSourceLock(providerOf(null));
        AtomicBoolean ran = new AtomicBoolean(false);

        boolean result = noProvider.withLock("drive-poll-1", Duration.ofMinutes(5),
                () -> ran.set(true));

        assertThat(result).isTrue();
        assertThat(ran).isTrue();
    }

    @Test
    void lock_configuration_uses_lock_name_and_lockAtMostFor() {
        when(provider.lock(any(LockConfiguration.class))).thenReturn(Optional.of(lock));

        helper.withLock("gmail-poll-pipeline-x-node-y", Duration.ofMinutes(7), () -> {});

        ArgumentCaptor<LockConfiguration> cfg = ArgumentCaptor.forClass(LockConfiguration.class);
        verify(provider, times(1)).lock(cfg.capture());
        assertThat(cfg.getValue().getName()).isEqualTo("gmail-poll-pipeline-x-node-y");
        assertThat(cfg.getValue().getLockAtMostFor()).isEqualTo(Duration.ofMinutes(7));
    }
}
