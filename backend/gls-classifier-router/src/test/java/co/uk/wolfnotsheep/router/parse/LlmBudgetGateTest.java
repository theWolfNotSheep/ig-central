package co.uk.wolfnotsheep.router.parse;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class LlmBudgetGateTest {

    private static final class StepClock extends Clock {
        private final AtomicReference<Instant> now;
        StepClock(Instant start) { this.now = new AtomicReference<>(start); }
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now.get(); }
        void advance(Duration d) { now.updateAndGet(t -> t.plus(d)); }
    }

    @Test
    void initial_state_is_not_exhausted() {
        LlmBudgetGate gate = new LlmBudgetGate();
        assertThat(gate.isExhausted()).isFalse();
        assertThat(gate.exhaustedUntil()).isNull();
    }

    @Test
    void markExhausted_sets_state_for_the_retry_window() {
        StepClock clock = new StepClock(Instant.parse("2026-04-30T12:00:00Z"));
        LlmBudgetGate gate = new LlmBudgetGate(clock);
        gate.markExhausted(Duration.ofMinutes(30));

        assertThat(gate.isExhausted()).isTrue();
        assertThat(gate.exhaustedUntil()).isEqualTo(Instant.parse("2026-04-30T12:30:00Z"));
    }

    @Test
    void state_clears_after_cooldown_lapses() {
        StepClock clock = new StepClock(Instant.parse("2026-04-30T12:00:00Z"));
        LlmBudgetGate gate = new LlmBudgetGate(clock);
        gate.markExhausted(Duration.ofMinutes(30));
        clock.advance(Duration.ofMinutes(31));

        assertThat(gate.isExhausted()).isFalse();
        assertThat(gate.exhaustedUntil()).isNull();
    }

    @Test
    void markExhausted_extends_window_when_new_until_is_later() {
        StepClock clock = new StepClock(Instant.parse("2026-04-30T12:00:00Z"));
        LlmBudgetGate gate = new LlmBudgetGate(clock);
        gate.markExhausted(Duration.ofMinutes(10));
        gate.markExhausted(Duration.ofMinutes(60));

        assertThat(gate.exhaustedUntil()).isEqualTo(Instant.parse("2026-04-30T13:00:00Z"));
    }

    @Test
    void markExhausted_does_not_shrink_window() {
        StepClock clock = new StepClock(Instant.parse("2026-04-30T12:00:00Z"));
        LlmBudgetGate gate = new LlmBudgetGate(clock);
        gate.markExhausted(Duration.ofMinutes(60));
        gate.markExhausted(Duration.ofMinutes(10));

        assertThat(gate.exhaustedUntil()).isEqualTo(Instant.parse("2026-04-30T13:00:00Z"));
    }

    @Test
    void markExhausted_with_zero_or_negative_floor_to_one_second() {
        StepClock clock = new StepClock(Instant.parse("2026-04-30T12:00:00Z"));
        LlmBudgetGate gate = new LlmBudgetGate(clock);
        gate.markExhausted(Duration.ZERO);
        assertThat(gate.exhaustedUntil()).isEqualTo(Instant.parse("2026-04-30T12:00:01Z"));

        gate.clear();
        gate.markExhausted(Duration.ofSeconds(-5));
        assertThat(gate.exhaustedUntil()).isEqualTo(Instant.parse("2026-04-30T12:00:01Z"));
    }

    @Test
    void markExhausted_with_null_floor_to_one_second() {
        StepClock clock = new StepClock(Instant.parse("2026-04-30T12:00:00Z"));
        LlmBudgetGate gate = new LlmBudgetGate(clock);
        gate.markExhausted(null);
        assertThat(gate.exhaustedUntil()).isEqualTo(Instant.parse("2026-04-30T12:00:01Z"));
    }

    @Test
    void clear_drops_state() {
        LlmBudgetGate gate = new LlmBudgetGate();
        gate.markExhausted(Duration.ofMinutes(30));
        gate.clear();
        assertThat(gate.isExhausted()).isFalse();
    }
}
