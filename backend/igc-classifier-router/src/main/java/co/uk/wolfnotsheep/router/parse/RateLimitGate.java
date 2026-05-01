package co.uk.wolfnotsheep.router.parse;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Per-replica rate-limit semaphore for the classifier-router's
 * {@code POST /v1/classify} surface. Bounds concurrent in-flight
 * cascade calls. {@link #acquire()} blocks for up to
 * {@code wait-ms} (default 0 = no wait) trying to grab a permit; if
 * it can't, throws {@link RateLimitExceededException} → 429
 * {@code ROUTER_RATE_LIMITED} with {@code Retry-After: 1}.
 *
 * <p>Set {@code igc.router.rate-limit.permits=0} (default) to disable
 * enforcement entirely — the gate becomes a pass-through.
 *
 * <p>Same shape as the existing {@code RateLimitGate} in
 * {@code igc-llm-worker} (Phase 1.6 PR4). Per-replica, not
 * cross-replica; cluster-wide rate limiting is a future enhancement.
 *
 * <p>Phase 2.6 — when a {@link MeterRegistry} is supplied, exposes
 * gauges {@code router.rate_limit.permits.available} and
 * {@code router.rate_limit.permits.total}.
 */
@Component
public class RateLimitGate {

    private static final Logger log = LoggerFactory.getLogger(RateLimitGate.class);

    private final int permits;
    private final long waitMs;
    private final Semaphore semaphore;

    public RateLimitGate(
            @Value("${igc.router.rate-limit.permits:0}") int permits,
            @Value("${igc.router.rate-limit.wait-ms:0}") long waitMs,
            ObjectProvider<MeterRegistry> meterRegistryProvider) {
        this.permits = Math.max(0, permits);
        this.waitMs = Math.max(0L, waitMs);
        this.semaphore = this.permits > 0 ? new Semaphore(this.permits, /* fair */ true) : null;
        if (this.permits > 0) {
            log.info("router: rate limit enabled — permits={} wait-ms={}", this.permits, this.waitMs);
            registerGauges(meterRegistryProvider);
        } else {
            log.info("router: rate limit disabled (igc.router.rate-limit.permits=0)");
        }
    }

    private void registerGauges(ObjectProvider<MeterRegistry> meterRegistryProvider) {
        MeterRegistry registry = meterRegistryProvider == null ? null : meterRegistryProvider.getIfAvailable();
        if (registry == null) return;
        Gauge.builder("router.rate_limit.permits.available", semaphore, Semaphore::availablePermits)
                .description("Currently-available permits on the per-replica router rate limit semaphore")
                .register(registry);
        Gauge.builder("router.rate_limit.permits.total", this, g -> g.permits)
                .description("Configured total permits on the router rate limit semaphore")
                .register(registry);
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
