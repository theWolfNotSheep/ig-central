package co.uk.wolfnotsheep.router.parse;

/**
 * Thrown when the per-replica router rate-limit semaphore can't issue
 * a permit within the configured wait window. Maps to RFC 7807 429
 * {@code ROUTER_RATE_LIMITED} with a {@code Retry-After: 1} header.
 */
public class RateLimitExceededException extends RuntimeException {

    public RateLimitExceededException(String message) {
        super(message);
    }
}
