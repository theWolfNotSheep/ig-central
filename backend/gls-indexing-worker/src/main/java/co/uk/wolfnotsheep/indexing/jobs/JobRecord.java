package co.uk.wolfnotsheep.indexing.jobs;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Mongo-mapped row in {@code indexing_jobs}. Backs both the
 * idempotency cache (sync `POST /v1/index/{documentId}`) and the
 * async-job poll surface (`POST /v1/reindex` → `GET /v1/jobs/{nodeRunId}`).
 * Same shape as the sibling enforcement-worker / slm-worker stores.
 */
@Document(collection = "indexing_jobs")
public record JobRecord(
        @Id String nodeRunId,
        JobState status,
        Instant acquiredAt,
        Instant startedAt,
        Instant completedAt,
        String resultJson,
        String errorCode,
        String errorMessage,
        @Indexed(expireAfter = "0s") Instant expiresAt
) {

    public boolean isTerminal() {
        return status == JobState.COMPLETED || status == JobState.FAILED;
    }

    public JobRecord withStatus(JobState newStatus, Instant when) {
        Instant started = (startedAt == null && newStatus == JobState.RUNNING) ? when : startedAt;
        Instant completed = (newStatus == JobState.COMPLETED || newStatus == JobState.FAILED) ? when : completedAt;
        return new JobRecord(nodeRunId, newStatus, acquiredAt, started, completed,
                resultJson, errorCode, errorMessage, expiresAt);
    }

    public JobRecord withResult(String json, Instant when) {
        return new JobRecord(nodeRunId, JobState.COMPLETED, acquiredAt,
                startedAt == null ? when : startedAt, when, json, null, null, expiresAt);
    }

    public JobRecord withFailure(String errorCode, String errorMessage, Instant when) {
        return new JobRecord(nodeRunId, JobState.FAILED, acquiredAt,
                startedAt == null ? when : startedAt, when, null, errorCode, errorMessage, expiresAt);
    }
}
