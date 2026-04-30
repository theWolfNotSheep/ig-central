package co.uk.wolfnotsheep.indexing.jobs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Combined idempotency + async-job store. Sync `POST /v1/index` and
 * async `POST /v1/reindex` share rows via {@code nodeRunId} per
 * CSV #47. Same shape as {@code gls-classifier-router} /
 * {@code gls-slm-worker} / {@code gls-enforcement-worker} — lifted
 * deliberately so observers and operational tooling can treat the
 * v2 services identically.
 */
@Service
public class JobStore {

    private static final Logger log = LoggerFactory.getLogger(JobStore.class);

    private final JobRepository repo;
    private final Duration ttl;

    public JobStore(
            JobRepository repo,
            @Value("${gls.indexing.worker.jobs.ttl:PT24H}") Duration ttl) {
        this.repo = repo;
        this.ttl = ttl;
    }

    public JobAcquisition tryAcquire(String nodeRunId) {
        Optional<JobRecord> existing = repo.findById(nodeRunId);
        if (existing.isPresent()) {
            return classify(existing.get());
        }
        Instant now = Instant.now();
        JobRecord row = new JobRecord(
                nodeRunId, JobState.PENDING, now, null, null, null, null, null,
                now.plus(ttl));
        try {
            repo.insert(row);
            return JobAcquisition.acquired();
        } catch (DuplicateKeyException raceLost) {
            log.debug("jobs: race lost on {}, re-reading", nodeRunId);
            return repo.findById(nodeRunId)
                    .map(JobStore::classify)
                    .orElse(JobAcquisition.acquired());
        }
    }

    private static JobAcquisition classify(JobRecord row) {
        return switch (row.status()) {
            case PENDING, RUNNING -> JobAcquisition.running(row);
            case COMPLETED -> JobAcquisition.completed(row);
            case FAILED -> JobAcquisition.failed(row);
        };
    }

    public JobRecord markRunning(String nodeRunId) {
        return repo.findById(nodeRunId).map(row -> {
            JobRecord updated = row.withStatus(JobState.RUNNING, Instant.now());
            repo.save(updated);
            return updated;
        }).orElseThrow(() -> new IllegalStateException("jobs: missing row " + nodeRunId));
    }

    public void markCompleted(String nodeRunId, String resultJson) {
        repo.findById(nodeRunId).ifPresent(row ->
                repo.save(row.withResult(resultJson, Instant.now())));
    }

    public void markFailed(String nodeRunId, String errorCode, String errorMessage) {
        repo.findById(nodeRunId).ifPresent(row ->
                repo.save(row.withFailure(errorCode, errorMessage, Instant.now())));
    }

    public Optional<JobRecord> find(String nodeRunId) {
        return repo.findById(nodeRunId);
    }

    public void release(String nodeRunId) {
        repo.deleteById(nodeRunId);
    }
}
