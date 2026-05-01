package co.uk.wolfnotsheep.extraction.archive.idempotency;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Mongo-mapped row in {@code archive_idempotency} per CSV #16.
 *
 * <p>One row per {@code nodeRunId}. {@code completedAt} flips from
 * null to a timestamp when the unpack finishes successfully;
 * {@code responseJson} caches the wire response so a retry returns
 * the same 200 without re-running the walker. Failures delete the row
 * so subsequent retries can start fresh — the alternative (leaving
 * a half-finished row in flight) would block recovery for the
 * 24h TTL window.
 *
 * <p>{@code expiresAt} is a Mongo TTL index — automatic cleanup so
 * stale rows from crashed in-flight unpacks don't accumulate.
 */
@Document(collection = "archive_idempotency")
public record IdempotencyRecord(
        @Id String nodeRunId,
        Instant acquiredAt,
        Instant completedAt,
        String responseJson,
        @Indexed(expireAfter = "0s") Instant expiresAt
) {

    public boolean isCompleted() {
        return completedAt != null;
    }

    public IdempotencyRecord withResult(String json, Instant when) {
        return new IdempotencyRecord(nodeRunId, acquiredAt, when, json, expiresAt);
    }
}
