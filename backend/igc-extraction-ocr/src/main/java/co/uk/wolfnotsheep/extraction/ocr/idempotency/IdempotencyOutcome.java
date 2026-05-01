package co.uk.wolfnotsheep.extraction.ocr.idempotency;

public record IdempotencyOutcome(Status status, String cachedJson) {

    public enum Status { ACQUIRED, IN_FLIGHT, CACHED }

    public static IdempotencyOutcome acquired() { return new IdempotencyOutcome(Status.ACQUIRED, null); }
    public static IdempotencyOutcome inFlight() { return new IdempotencyOutcome(Status.IN_FLIGHT, null); }
    public static IdempotencyOutcome cached(String json) { return new IdempotencyOutcome(Status.CACHED, json); }
}
