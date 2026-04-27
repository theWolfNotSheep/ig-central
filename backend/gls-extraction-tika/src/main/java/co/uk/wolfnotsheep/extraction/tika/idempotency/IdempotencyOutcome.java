package co.uk.wolfnotsheep.extraction.tika.idempotency;

/**
 * What {@link IdempotencyStore#tryAcquire} found.
 *
 * <ul>
 *     <li>{@link #ACQUIRED} — first time this {@code nodeRunId} is being
 *         processed; caller proceeds with the work.</li>
 *     <li>{@link #IN_FLIGHT} — an earlier call is still running. Caller
 *         returns 409 IN_FLIGHT per the asyncapi / OpenAPI contract.</li>
 *     <li>{@link #CACHED} — an earlier call completed; caller returns
 *         the cached response (deserialised from
 *         {@link IdempotencyRecord#responseJson}).</li>
 * </ul>
 *
 * @param status       One of the three states above.
 * @param cachedJson   Cached response JSON when {@code status=CACHED};
 *                     null otherwise.
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
