package co.uk.wolfnotsheep.extraction.ocr.idempotency;

public class IdempotencyInFlightException extends RuntimeException {
    public IdempotencyInFlightException(String nodeRunId) {
        super("an OCR for nodeRunId " + nodeRunId + " is still in flight");
    }
}
