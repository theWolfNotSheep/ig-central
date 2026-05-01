package co.uk.wolfnotsheep.llmworker.backend;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Per-replica rate-limit semaphore. Bounds concurrent in-flight LLM
 * calls. {@link #acquire()} blocks for up to {@code wait-ms} (default
 * 0 = no wait) trying to grab a permit; if it can't, throws
 * {@link RateLimitExceededException} → 429
 * {@code LLM_RATE_LIMITED}.
 *
 * <p>Set {@code igc.llm.worker.rate-limit.permits=0} (default) to
 * disable enforcement entirely — the gate becomes a pass-through.
 *
 * <p>Phase 1.6 PR4 — the gate is per-replica, not cross-replica. A
 * cluster-wide rate limit (Redis-backed token bucket) is a future
 * enhancement once a real load profile exists.
 */
@Component
public class RateLimitGate {

    private static final Logger log = LoggerFactory.getLogger(RateLimitGate.class);

    private final int permits;
    private final long waitMs;
    private final Semaphore semaphore;

    public RateLimitGate(
            @Value("${igc.llm.worker.rate-limit.permits:0}") int permits,
            @Value("${igc.llm.worker.rate-limit.wait-ms:0}") long waitMs) {
        this.permits = Math.max(0, permits);
        this.waitMs = Math.max(0L, waitMs);
        this.semaphore = this.permits > 0 ? new Semaphore(this.permits, /* fair */ true) : null;
        if (this.permits > 0) {
            log.info("llm: rate limit enabled — permits={} wait-ms={}", this.permits, this.waitMs);
        } else {
            log.info("llm: rate limit disabled (igc.llm.worker.rate-limit.permits=0)");
        }
    }

    /**
     * Acquire a permit. Returns a token to release. Blocks for up to
     * {@code wait-ms} when no permit is immediately available.
     */
    public Token acquire() {
        if (semaphore == null) return Token.NO_OP;
        try {
            boolean acquired = semaphore.tryAcquire(waitMs, TimeUnit.MILLISECONDS);
            if (!acquired) {
                throw new RateLimitExceededException(
                        "no permits available after " + waitMs + "ms — try again later");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RateLimitExceededException(
                    "interrupted waiting for permit: " + e.getMessage());
        }
        return new Token(this);
    }

    void release() {
        if (semaphore != null) semaphore.release();
    }

    public int availablePermits() {
        return semaphore == null ? -1 : semaphore.availablePermits();
    }

    public int totalPermits() {
        return permits;
    }

    /**
     * Token returned by {@link #acquire}. Implements {@link AutoCloseable}
     * so callers can use try-with-resources to guarantee release.
     */
    public static final class Token implements AutoCloseable {

        static final Token NO_OP = new Token(null);

        private final RateLimitGate gate;
        private boolean released;

        Token(RateLimitGate gate) {
            this.gate = gate;
        }

        @Override
        public void close() {
            if (!released && gate != null) {
                gate.release();
                released = true;
            }
        }
    }
}
