package co.uk.wolfnotsheep.router.parse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Phase 2.2 PR3 — auto-degrade-to-SLM gate for the LLM tier.
 *
 * <p>When the LLM worker returns {@code 429 LLM_BUDGET_EXCEEDED}, the
 * cascade router marks this gate exhausted until the worker's
 * {@code Retry-After} (or the configured fallback). While exhausted,
 * the LLM dispatcher short-circuits — every call throws an immediate
 * {@code LLM_BUDGET_EXHAUSTED} fallthrough so the cascade can fall back
 * to the SLM tier without waiting on a synchronous HTTP round-trip
 * that would just 429 again.
 *
 * <p>State recovers automatically when the cool-down expires; no
 * explicit "ok again" probe — the next call attempts dispatch and either
 * succeeds (clearing nothing visible because we never set anything)
 * or hits another 429 (re-arming the gate).
 *
 * <p>Thread-safe via {@link AtomicReference}. The contention model is
 * benign — many readers checking {@link #isExhausted()}, occasional
 * writers via {@link #markExhausted(Duration)}.
 */
public class LlmBudgetGate {

    private static final Logger log = LoggerFactory.getLogger(LlmBudgetGate.class);

    private final Clock clock;
    private final AtomicReference<Instant> exhaustedUntil = new AtomicReference<>();

    public LlmBudgetGate() {
        this(Clock.systemUTC());
    }

    /** Visible for testing — inject a fake clock. */
    public LlmBudgetGate(Clock clock) {
        this.clock = clock;
    }

    /** @return whether the gate is currently in the exhausted state. */
    public boolean isExhausted() {
        Instant until = exhaustedUntil.get();
        if (until == null) return false;
        if (Instant.now(clock).isAfter(until)) {
            // Cool-down has elapsed — clear lazily so the next caller
            // sees a clean state without us having to schedule cleanup.
            exhaustedUntil.compareAndSet(until, null);
            return false;
        }
        return true;
    }

    /** @return when the gate clears, or {@code null} if not exhausted. */
    public Instant exhaustedUntil() {
        Instant until = exhaustedUntil.get();
        if (until == null) return null;
        if (Instant.now(clock).isAfter(until)) {
            exhaustedUntil.compareAndSet(until, null);
            return null;
        }
        return until;
    }

    /**
     * Mark the gate exhausted for {@code retryAfter}. Idempotent — if
     * already exhausted, the {@code until} time is set to the later of
     * the existing and new values (the worker's hint trumps stale
     * state).
     */
    public void markExhausted(Duration retryAfter) {
        if (retryAfter == null || retryAfter.isNegative() || retryAfter.isZero()) {
            // Defensive — a worker that says "retry now" gets a 1s floor
            // so we don't hot-loop.
            retryAfter = Duration.ofSeconds(1);
        }
        Instant target = Instant.now(clock).plus(retryAfter);
        exhaustedUntil.updateAndGet(current ->
                current == null || target.isAfter(current) ? target : current);
        log.warn("LLM budget gate: exhausted until {} (retryAfter={})", target, retryAfter);
    }

    /** Visible for testing — drop the gate state. */
    void clear() {
        exhaustedUntil.set(null);
    }
}
