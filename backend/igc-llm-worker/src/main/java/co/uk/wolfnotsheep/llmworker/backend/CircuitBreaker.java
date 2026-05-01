package co.uk.wolfnotsheep.llmworker.backend;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Three-state circuit breaker — generic, no LLM-specific knowledge.
 *
 * <p>States and transitions:
 * <ul>
 *   <li>{@link State#CLOSED} — normal operation. Calls go through.
 *       Successes reset the failure counter; failures increment it.
 *       After {@code failureThreshold} consecutive failures, transitions
 *       to {@link State#OPEN}.</li>
 *   <li>{@link State#OPEN} — failing fast. Calls are rejected without
 *       being attempted ({@link #beforeCall()} returns {@code false}).
 *       After {@code openCooldown}, the next call attempt transitions
 *       to {@link State#HALF_OPEN}.</li>
 *   <li>{@link State#HALF_OPEN} — probing. A single call is allowed
 *       through. Success → {@link State#CLOSED}; failure → {@link State#OPEN}
 *       (cooldown restarts).</li>
 * </ul>
 *
 * <p>Thread-safety: concurrent reads of {@link #currentState()} are
 * always consistent (atomic reference). Concurrent {@link #beforeCall}
 * calls in the {@link State#OPEN}-to-{@link State#HALF_OPEN} window may
 * each see CLOSED-look behaviour for an instant; in practice only one
 * thread will succeed in transitioning the state because of the CAS.
 * The breaker is intended as a coarse guard, not a precise rate limiter.
 */
public final class CircuitBreaker {

    private static final Logger log = LoggerFactory.getLogger(CircuitBreaker.class);

    public enum State { CLOSED, OPEN, HALF_OPEN }

    private final String name;
    private final int failureThreshold;
    private final Duration openCooldown;
    private final Clock clock;

    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
    private final AtomicReference<Instant> openedAt = new AtomicReference<>();
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);

    public CircuitBreaker(String name, int failureThreshold, Duration openCooldown) {
        this(name, failureThreshold, openCooldown, Clock.systemUTC());
    }

    /** Visible for testing — inject a fake clock. */
    public CircuitBreaker(String name, int failureThreshold, Duration openCooldown, Clock clock) {
        if (failureThreshold <= 0) {
            throw new IllegalArgumentException("failureThreshold must be > 0");
        }
        if (openCooldown == null || openCooldown.isNegative() || openCooldown.isZero()) {
            throw new IllegalArgumentException("openCooldown must be > 0");
        }
        this.name = name == null || name.isBlank() ? "circuit-breaker" : name;
        this.failureThreshold = failureThreshold;
        this.openCooldown = openCooldown;
        this.clock = clock;
    }

    /**
     * Test the breaker before making a call.
     *
     * @return {@code true} if the call should proceed; {@code false} if
     *         the breaker is OPEN and the cooldown hasn't elapsed.
     */
    public boolean beforeCall() {
        State current = state.get();
        if (current == State.CLOSED || current == State.HALF_OPEN) {
            return true;
        }
        // OPEN — check whether cooldown has elapsed.
        Instant openedAtSnapshot = openedAt.get();
        if (openedAtSnapshot == null) {
            // Defensive — shouldn't happen, but treat as "ready to probe".
            transitionTo(State.OPEN, State.HALF_OPEN);
            return true;
        }
        Instant now = Instant.now(clock);
        if (now.isAfter(openedAtSnapshot.plus(openCooldown))) {
            transitionTo(State.OPEN, State.HALF_OPEN);
            return true;
        }
        return false;
    }

    /** Record a successful call. Resets the breaker to CLOSED. */
    public void recordSuccess() {
        State current = state.get();
        consecutiveFailures.set(0);
        if (current == State.HALF_OPEN || current == State.OPEN) {
            transitionTo(current, State.CLOSED);
            openedAt.set(null);
            log.info("circuit breaker {}: success after {} — closing", name, current);
        }
    }

    /** Record a failed call. Trips the breaker on threshold or HALF_OPEN failure. */
    public void recordFailure() {
        State current = state.get();
        if (current == State.HALF_OPEN) {
            // The probe failed — re-open and restart the cooldown.
            openedAt.set(Instant.now(clock));
            transitionTo(State.HALF_OPEN, State.OPEN);
            log.warn("circuit breaker {}: HALF_OPEN probe failed — re-opening for {}", name, openCooldown);
            return;
        }
        if (current == State.OPEN) {
            // Already open; nothing to do (calls weren't supposed to reach here anyway).
            return;
        }
        // CLOSED
        int failures = consecutiveFailures.incrementAndGet();
        if (failures >= failureThreshold) {
            openedAt.set(Instant.now(clock));
            if (transitionTo(State.CLOSED, State.OPEN)) {
                log.warn("circuit breaker {}: {} consecutive failures — opening for {}",
                        name, failures, openCooldown);
            }
        }
    }

    public State currentState() {
        return state.get();
    }

    public int consecutiveFailures() {
        return consecutiveFailures.get();
    }

    private boolean transitionTo(State expected, State next) {
        return state.compareAndSet(expected, next);
    }
}
