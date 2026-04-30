package co.uk.wolfnotsheep.indexing.web;

import co.uk.wolfnotsheep.indexing.api.ReindexApi;
import co.uk.wolfnotsheep.indexing.jobs.JobAcquisition;
import co.uk.wolfnotsheep.indexing.jobs.JobStore;
import co.uk.wolfnotsheep.indexing.model.JobAccepted;
import co.uk.wolfnotsheep.indexing.model.ReindexRequest;
import co.uk.wolfnotsheep.indexing.service.IndexingService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * Async bulk reindex. Always 202 + `Location: /v1/jobs/{nodeRunId}`;
 * background work runs through the {@link AsyncDispatcher} pool.
 */
@RestController
public class ReindexController implements ReindexApi {

    private static final Logger log = LoggerFactory.getLogger(ReindexController.class);

    private final IndexingService indexingService;
    private final JobStore jobs;
    private final AsyncDispatcher asyncDispatcher;
    private final ObjectMapper mapper;

    public ReindexController(IndexingService indexingService, JobStore jobs,
                             AsyncDispatcher asyncDispatcher, ObjectMapper mapper) {
        this.indexingService = indexingService;
        this.jobs = jobs;
        this.asyncDispatcher = asyncDispatcher;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<JobAccepted> reindex(
            String traceparent, ReindexRequest request, String idempotencyKey) {

        String nodeRunId = request.getNodeRunId();
        JobAcquisition acq = jobs.tryAcquire(nodeRunId);
        return switch (acq.status()) {
            case ACQUIRED -> {
                asyncDispatcher.dispatch(request);
                yield acceptedFor(nodeRunId);
            }
            case RUNNING -> { throw new JobInFlightException(nodeRunId); }
            case COMPLETED, FAILED -> acceptedFor(nodeRunId); // already terminal — caller polls /v1/jobs to see the row
        };
    }

    private ResponseEntity<JobAccepted> acceptedFor(String nodeRunId) {
        URI poll = URI.create("/v1/jobs/" + nodeRunId);
        JobAccepted body = new JobAccepted();
        body.setNodeRunId(nodeRunId);
        body.setStatus(JobAccepted.StatusEnum.PENDING);
        body.setPollUrl(poll);
        return ResponseEntity.accepted().header(HttpHeaders.LOCATION, poll.toString()).body(body);
    }

    /** Background path. Package-private so {@link AsyncDispatcher} can {@code @Async}-invoke it. */
    void runAsync(ReindexRequest request) {
        String nodeRunId = request.getNodeRunId();
        try {
            jobs.markRunning(nodeRunId);
            IndexingService.ReindexSummary summary = indexingService.reindexAll(request.getStatusFilter());
            cacheCompleted(nodeRunId, summary);
        } catch (RuntimeException failure) {
            log.error("reindex async failure for {}: {}", nodeRunId, failure.getMessage(), failure);
            jobs.markFailed(nodeRunId, "INDEX_UNEXPECTED",
                    failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage());
        }
    }

    private void cacheCompleted(String nodeRunId, IndexingService.ReindexSummary summary) {
        try {
            // We persist the summary on the job row; JobController surfaces it as JobStatus.summary.
            jobs.markCompleted(nodeRunId, mapper.writeValueAsString(summary));
        } catch (JsonProcessingException e) {
            log.warn("reindex summary cache write failed for nodeRunId={}: {}", nodeRunId, e.getMessage());
        }
    }
}
