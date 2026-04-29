package co.uk.wolfnotsheep.router.idempotency;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "router_idempotency")
public record IdempotencyRecord(
        @Id String nodeRunId,
        Instant acquiredAt,
        Instant completedAt,
        String responseJson,
        @Indexed(expireAfter = "0s") Instant expiresAt
) {
    public boolean isCompleted() { return completedAt != null; }

    public IdempotencyRecord withResult(String json, Instant when) {
        return new IdempotencyRecord(nodeRunId, acquiredAt, when, json, expiresAt);
    }
}
