package co.uk.wolfnotsheep.llmworker.backend;

/**
 * Thrown when the per-replica rate-limit semaphore can't acquire a
 * permit within the configured wait window. Mapped to 429
 * {@code LLM_RATE_LIMITED} with a {@code Retry-After: 1} header — a
 * short backoff, since the semaphore frees up as soon as another
 * call completes.
 */
public class RateLimitExceededException extends RuntimeException {

    public RateLimitExceededException(String message) {
        super(message);
    }
}
