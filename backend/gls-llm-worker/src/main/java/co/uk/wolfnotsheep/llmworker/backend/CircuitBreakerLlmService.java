package co.uk.wolfnotsheep.llmworker.backend;

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
 */
public class CircuitBreakerLlmService implements LlmService {

    private static final Logger log = LoggerFactory.getLogger(CircuitBreakerLlmService.class);

    private final LlmService delegate;
    private final CircuitBreaker breaker;

    public CircuitBreakerLlmService(LlmService delegate, CircuitBreaker breaker) {
        this.delegate = delegate;
        this.breaker = breaker;
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
