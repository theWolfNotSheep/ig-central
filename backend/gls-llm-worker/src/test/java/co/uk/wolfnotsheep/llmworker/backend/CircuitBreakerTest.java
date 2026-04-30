package co.uk.wolfnotsheep.llmworker.backend;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CircuitBreakerTest {

    /** Manually-tickable clock — start at epoch, advance via {@link #advance(Duration)}. */
    private static final class StepClock extends Clock {
        private final AtomicReference<Instant> now;

        StepClock(Instant start) { this.now = new AtomicReference<>(start); }

        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now.get(); }
        void advance(Duration d) { now.updateAndGet(t -> t.plus(d)); }
    }

    @Test
    void initial_state_is_CLOSED() {
        CircuitBreaker cb = new CircuitBreaker("t", 3, Duration.ofSeconds(30));
        assertThat(cb.currentState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(cb.beforeCall()).isTrue();
    }

    @Test
    void failures_below_threshold_keep_breaker_CLOSED() {
        CircuitBreaker cb = new CircuitBreaker("t", 3, Duration.ofSeconds(30));
        cb.recordFailure();
        cb.recordFailure();
        assertThat(cb.currentState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(cb.beforeCall()).isTrue();
        assertThat(cb.consecutiveFailures()).isEqualTo(2);
    }

    @Test
    void threshold_failures_open_the_breaker() {
        CircuitBreaker cb = new CircuitBreaker("t", 3, Duration.ofSeconds(30));
        cb.recordFailure();
        cb.recordFailure();
        cb.recordFailure();
        assertThat(cb.currentState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(cb.beforeCall()).isFalse();
    }

    @Test
    void success_resets_consecutive_failures_in_CLOSED() {
        CircuitBreaker cb = new CircuitBreaker("t", 3, Duration.ofSeconds(30));
        cb.recordFailure();
        cb.recordFailure();
        cb.recordSuccess();
        assertThat(cb.consecutiveFailures()).isZero();
        cb.recordFailure();
        cb.recordFailure();
        assertThat(cb.currentState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void OPEN_transitions_to_HALF_OPEN_after_cooldown() {
        StepClock clock = new StepClock(Instant.parse("2026-04-30T12:00:00Z"));
        CircuitBreaker cb = new CircuitBreaker("t", 2, Duration.ofSeconds(30), clock);
        cb.recordFailure();
        cb.recordFailure();
        assertThat(cb.currentState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(cb.beforeCall()).isFalse();

        clock.advance(Duration.ofSeconds(31));
        assertThat(cb.beforeCall()).isTrue();
        assertThat(cb.currentState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);
    }

    @Test
    void HALF_OPEN_success_closes_the_breaker() {
        StepClock clock = new StepClock(Instant.parse("2026-04-30T12:00:00Z"));
        CircuitBreaker cb = new CircuitBreaker("t", 2, Duration.ofSeconds(30), clock);
        cb.recordFailure();
        cb.recordFailure();
        clock.advance(Duration.ofSeconds(31));
        cb.beforeCall(); // HALF_OPEN
        cb.recordSuccess();

        assertThat(cb.currentState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(cb.consecutiveFailures()).isZero();
    }

    @Test
    void HALF_OPEN_failure_re_opens_with_fresh_cooldown() {
        StepClock clock = new StepClock(Instant.parse("2026-04-30T12:00:00Z"));
        CircuitBreaker cb = new CircuitBreaker("t", 2, Duration.ofSeconds(30), clock);
        cb.recordFailure();
        cb.recordFailure();
        clock.advance(Duration.ofSeconds(31));
        cb.beforeCall(); // HALF_OPEN
        cb.recordFailure(); // probe failed

        assertThat(cb.currentState()).isEqualTo(CircuitBreaker.State.OPEN);
        // Cooldown restarts — call still rejected immediately.
        assertThat(cb.beforeCall()).isFalse();
        clock.advance(Duration.ofSeconds(31));
        assertThat(cb.beforeCall()).isTrue();
    }

    @Test
    void OPEN_with_no_cooldown_elapsed_keeps_rejecting() {
        StepClock clock = new StepClock(Instant.parse("2026-04-30T12:00:00Z"));
        CircuitBreaker cb = new CircuitBreaker("t", 1, Duration.ofMinutes(5), clock);
        cb.recordFailure();
        clock.advance(Duration.ofSeconds(1));
        assertThat(cb.beforeCall()).isFalse();
        clock.advance(Duration.ofMinutes(2));
        assertThat(cb.beforeCall()).isFalse();
    }

    @Test
    void invalid_config_is_rejected() {
        assertThatThrownBy(() -> new CircuitBreaker("t", 0, Duration.ofSeconds(30)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CircuitBreaker("t", -1, Duration.ofSeconds(30)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CircuitBreaker("t", 3, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CircuitBreaker("t", 3, Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CircuitBreaker("t", 3, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
