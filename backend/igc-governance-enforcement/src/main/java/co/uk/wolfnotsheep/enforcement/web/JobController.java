package co.uk.wolfnotsheep.enforcement.web;

import co.uk.wolfnotsheep.enforcement.api.JobsApi;
import co.uk.wolfnotsheep.enforcement.jobs.JobRecord;
import co.uk.wolfnotsheep.enforcement.jobs.JobStore;
import co.uk.wolfnotsheep.enforcement.model.EnforceResponse;
import co.uk.wolfnotsheep.enforcement.model.JobStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@RestController
public class JobController implements JobsApi {

    private static final Logger log = LoggerFactory.getLogger(JobController.class);

    private final JobStore jobs;
    private final ObjectMapper mapper;

    public JobController(JobStore jobs, ObjectMapper mapper) {
        this.jobs = jobs;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<JobStatus> getJob(String nodeRunId) {
        JobRecord row = jobs.find(nodeRunId).orElseThrow(() -> new JobNotFoundException(nodeRunId));
        JobStatus body = new JobStatus();
        body.setNodeRunId(row.nodeRunId());
        body.setStatus(toApi(row));
        if (row.startedAt() != null) {
            body.setStartedAt(OffsetDateTime.ofInstant(row.startedAt(), ZoneOffset.UTC));
        }
        if (row.completedAt() != null) {
            body.setCompletedAt(OffsetDateTime.ofInstant(row.completedAt(), ZoneOffset.UTC));
        }
        if (row.resultJson() != null) {
            try {
                body.setResult(mapper.readValue(row.resultJson(), EnforceResponse.class));
            } catch (Exception e) {
                log.warn("job result deserialise failed for {}: {}", nodeRunId, e.getMessage());
            }
        }
        if (row.errorCode() != null) body.setErrorCode(row.errorCode());
        if (row.errorMessage() != null) body.setErrorMessage(row.errorMessage());
        return ResponseEntity.ok(body);
    }

    private static JobStatus.StatusEnum toApi(JobRecord row) {
        return switch (row.status()) {
            case PENDING -> JobStatus.StatusEnum.PENDING;
            case RUNNING -> JobStatus.StatusEnum.RUNNING;
            case COMPLETED -> JobStatus.StatusEnum.COMPLETED;
            case FAILED -> JobStatus.StatusEnum.FAILED;
        };
    }
}
