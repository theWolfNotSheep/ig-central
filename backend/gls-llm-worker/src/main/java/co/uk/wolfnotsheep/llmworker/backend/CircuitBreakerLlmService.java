package co.uk.wolfnotsheep.llmworker.backend;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Decorator that wraps an {@link LlmService} with a {@link CircuitBreaker}.
 *
 * <p>Counts {@link RuntimeException} from the underlying service as a
 * failure (which is the contract of {@link AnthropicLlmService} and
 * {@link OllamaLlmService} for transport / 5xx errors). Counts
 * {@link BudgetExceededException} and {@link RateLimitExceededException}
 * as <em>not</em> failures — those are caller-side gates, not upstream
 * health signals.
 *
 * <p>When the breaker is OPEN, throws {@link CircuitBreakerOpenException}
 * without calling the underlying service. The cascade router translates
 * this to a fall-through to the next tier.
 *
 * <p>Phase 2.6 — when a {@link MeterRegistry} is provided at construction,
 * exposes two gauges tagged by {@code backend}:
 * <ul>
 *   <li>{@code llm.circuit_breaker.state} — 0=CLOSED, 1=HALF_OPEN, 2=OPEN.</li>
 *   <li>{@code llm.circuit_breaker.consecutive_failures} — current run-length
 *       of failures in CLOSED state (resets to 0 on success or transition).</li>
 * </ul>
 */
public class CircuitBreakerLlmService implements LlmService {

    private static final Logger log = LoggerFactory.getLogger(CircuitBreakerLlmService.class);

    private final LlmService delegate;
    private final CircuitBreaker breaker;

    public CircuitBreakerLlmService(LlmService delegate, CircuitBreaker breaker) {
        this(delegate, breaker, null);
    }

    public CircuitBreakerLlmService(LlmService delegate, CircuitBreaker breaker,
                                    MeterRegistry meterRegistry) {
        this.delegate = delegate;
        this.breaker = breaker;
        if (meterRegistry != null) {
            registerGauges(meterRegistry, delegate.activeBackend().name().toLowerCase());
        }
    }

    private void registerGauges(MeterRegistry registry, String backend) {
        Tags tags = Tags.of("backend", backend);
        Gauge.builder("llm.circuit_breaker.state", breaker, b -> stateAsDouble(b.currentState()))
                .description("Circuit breaker state — 0=CLOSED, 1=HALF_OPEN, 2=OPEN")
                .tags(tags)
                .register(registry);
        Gauge.builder("llm.circuit_breaker.consecutive_failures", breaker, CircuitBreaker::consecutiveFailures)
                .description("Consecutive upstream failures since the last success / state transition")
                .tags(tags)
                .register(registry);
    }

    private static double stateAsDouble(CircuitBreaker.State s) {
        return switch (s) {
            case CLOSED -> 0.0;
            case HALF_OPEN -> 1.0;
            case OPEN -> 2.0;
        };
    }

    @Override
    public LlmResult classify(String blockId, Integer blockVersion, String text) {
        if (!breaker.beforeCall()) {
            log.debug("circuit breaker open for {} — short-circuiting", delegate.activeBackend());
            throw new CircuitBreakerOpenException(delegate.activeBackend().name());
        }
        try {
            LlmResult result = delegate.classify(blockId, blockVersion, text);
            breaker.recordSuccess();
            return result;
        } catch (BudgetExceededException | RateLimitExceededException e) {
            // Caller-side gates — not an upstream-health signal.
            throw e;
        } catch (RuntimeException e) {
            breaker.recordFailure();
            throw e;
        }
    }

    @Override
    public LlmBackendId activeBackend() {
        return delegate.activeBackend();
    }

    @Override
    public boolean isReady() {
        // OPEN breaker → not ready for calls.
        if (breaker.currentState() == CircuitBreaker.State.OPEN) {
            return false;
        }
        return delegate.isReady();
    }

    /** Visible for testing + observability. */
    public CircuitBreaker breaker() {
        return breaker;
    }

    /** Visible for testing. */
    LlmService delegate() {
        return delegate;
    }
}
