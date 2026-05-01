package co.uk.wolfnotsheep.extraction.archive.idempotency;

/**
 * What {@link IdempotencyStore#tryAcquire} found.
 *
 * <ul>
 *     <li>{@code ACQUIRED} — first time this {@code nodeRunId} is being
 *         processed; caller proceeds with the work.</li>
 *     <li>{@code IN_FLIGHT} — an earlier call is still running. Caller
 *         returns 409 IN_FLIGHT per the OpenAPI contract.</li>
 *     <li>{@code CACHED} — an earlier call completed; caller returns
 *         the cached response (deserialised from
 *         {@link IdempotencyRecord#responseJson}).</li>
 * </ul>
 */
public record IdempotencyOutcome(Status status, String cachedJson) {

    public enum Status {
        ACQUIRED,
        IN_FLIGHT,
        CACHED
    }

    public static IdempotencyOutcome acquired() {
        return new IdempotencyOutcome(Status.ACQUIRED, null);
    }

    public static IdempotencyOutcome inFlight() {
        return new IdempotencyOutcome(Status.IN_FLIGHT, null);
    }

    public static IdempotencyOutcome cached(String json) {
        return new IdempotencyOutcome(Status.CACHED, json);
    }
}
