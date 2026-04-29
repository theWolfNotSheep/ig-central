package co.uk.wolfnotsheep.llmworker.backend;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CostBudgetTrackerTest {

    @Test
    void disabled_when_cap_is_zero() {
        CostBudgetTracker tracker = new CostBudgetTracker(0L);
        // No matter how much we record, checkBudget never throws.
        tracker.recordUsage(10_000_000L, 5_000_000L);
        tracker.checkBudget(); // should not throw
        assertThat(tracker.dailyTokenCap()).isEqualTo(0L);
    }

    @Test
    void allows_calls_until_cap_reached() {
        CostBudgetTracker tracker = new CostBudgetTracker(1000L);
        tracker.checkBudget(); // 0 < 1000
        tracker.recordUsage(400L, 200L); // running 600

        tracker.checkBudget(); // 600 < 1000
        tracker.recordUsage(300L, 100L); // running 1000
    }

    @Test
    void rejects_when_cap_exceeded_with_BudgetExceededException() {
        CostBudgetTracker tracker = new CostBudgetTracker(1000L);
        tracker.recordUsage(600L, 500L); // running 1100

        assertThatThrownBy(tracker::checkBudget)
                .isInstanceOf(BudgetExceededException.class)
                .hasMessageContaining(">= 1000");
    }

    @Test
    void retryAfter_is_within_24_hours() {
        CostBudgetTracker tracker = new CostBudgetTracker(100L);
        tracker.recordUsage(60L, 50L); // 110 over

        try {
            tracker.checkBudget();
        } catch (BudgetExceededException e) {
            assertThat(e.retryAfterSeconds()).isPositive().isLessThanOrEqualTo(86_400L);
        }
    }

    @Test
    void rolls_over_at_UTC_midnight() {
        // Frozen clock at 23:59:30 UTC on 2026-04-29.
        Instant evening = Instant.parse("2026-04-29T23:59:30Z");
        TestClock clock = new TestClock(evening);
        CostBudgetTracker tracker = new CostBudgetTracker(1000L, clock);

        tracker.recordUsage(900L, 0L);
        assertThat(tracker.currentDayUsage()).isEqualTo(900L);

        // Advance to next day 00:00:30.
        clock.set(Instant.parse("2026-04-30T00:00:30Z"));
        // Next call resets via rolloverIfNeeded.
        assertThat(tracker.currentDayUsage()).isEqualTo(0L);
        tracker.checkBudget(); // should not throw — fresh day
    }

    @Test
    void recordUsage_with_zero_total_is_a_noop() {
        CostBudgetTracker tracker = new CostBudgetTracker(100L);
        tracker.recordUsage(0L, 0L);
        assertThat(tracker.currentDayUsage()).isEqualTo(0L);
        // Negative also no-ops.
        tracker.recordUsage(-5L, 0L);
        assertThat(tracker.currentDayUsage()).isEqualTo(0L);
    }

    /** Minimal Clock impl for tests — settable instant. */
    private static final class TestClock extends Clock {
        private volatile Instant now;

        TestClock(Instant initial) {
            this.now = initial;
        }

        void set(Instant when) {
            this.now = when;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
