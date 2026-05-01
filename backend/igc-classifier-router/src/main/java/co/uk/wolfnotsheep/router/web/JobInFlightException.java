package co.uk.wolfnotsheep.router.web;

/**
 * Sync request collided with a sibling that's mid-flight (PENDING /
 * RUNNING) or with a previously failed run that hasn't been
 * explicitly cleared. Mapped to RFC 7807
 * {@code IDEMPOTENCY_IN_FLIGHT} / 409.
 */
public class JobInFlightException extends RuntimeException {
    public JobInFlightException(String nodeRunId) {
        super("a router classify call for nodeRunId " + nodeRunId + " is still in flight");
    }
}
