package co.uk.wolfnotsheep.llmworker.backend;

/**
 * Thrown when a {@link CircuitBreakerLlmService} short-circuits a call
 * because its breaker is in {@link CircuitBreaker.State#OPEN}.
 *
 * <p>Maps to {@code 503 Service Unavailable} with code
 * {@code LLM_UPSTREAM_UNAVAILABLE} via the controller's exception handler.
 */
public class CircuitBreakerOpenException extends RuntimeException {

    public CircuitBreakerOpenException(String backend) {
        super("circuit breaker open for backend " + backend);
    }
}
