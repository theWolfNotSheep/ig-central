package co.uk.wolfnotsheep.extraction.tika.idempotency;

/**
 * Thrown when {@link IdempotencyStore#tryAcquire} reports
 * {@code IN_FLIGHT}. Mapped to RFC 7807 with code
 * {@code IDEMPOTENCY_IN_FLIGHT} and HTTP 409.
 */
public class IdempotencyInFlightException extends RuntimeException {
    public IdempotencyInFlightException(String nodeRunId) {
        super("an extraction for nodeRunId " + nodeRunId + " is still in flight");
    }
}
