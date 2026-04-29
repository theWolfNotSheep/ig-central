package co.uk.wolfnotsheep.router.idempotency;

public class IdempotencyInFlightException extends RuntimeException {
    public IdempotencyInFlightException(String nodeRunId) {
        super("a classify call for nodeRunId " + nodeRunId + " is still in flight");
    }
}
