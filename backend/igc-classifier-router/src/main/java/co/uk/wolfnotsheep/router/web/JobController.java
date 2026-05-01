package co.uk.wolfnotsheep.router.web;

import co.uk.wolfnotsheep.router.api.JobsApi;
import co.uk.wolfnotsheep.router.jobs.JobRecord;
import co.uk.wolfnotsheep.router.jobs.JobStore;
import co.uk.wolfnotsheep.router.model.ClassifyRequestText;
import co.uk.wolfnotsheep.router.model.ClassifyRequestTextOneOf;
import co.uk.wolfnotsheep.router.model.ClassifyRequestTextOneOf1;
import co.uk.wolfnotsheep.router.model.ClassifyResponse;
import co.uk.wolfnotsheep.router.model.JobStatus;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Implements {@code GET /v1/jobs/{nodeRunId}} — clients poll this
 * after `Prefer: respond-async` returns a 202.
 */
@RestController
public class JobController implements JobsApi {

    private static final Logger log = LoggerFactory.getLogger(JobController.class);

    private final JobStore jobs;
    private final ObjectMapper mapper;

    public JobController(JobStore jobs, ObjectMapper mapper) {
        this.jobs = jobs;
        // The cached ClassifyResponse may carry a oneOf TextPayload on
        // request shape; we don't surface request payloads via the job
        // status, but the response's nested objects pass through.
        this.mapper = mapper.copy()
                .addMixIn(ClassifyRequestText.class, ClassifyRequestTextMixin.class);
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
                body.setResult(mapper.readValue(row.resultJson(), ClassifyResponse.class));
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

    @JsonTypeInfo(use = JsonTypeInfo.Id.DEDUCTION)
    @JsonSubTypes({
            @JsonSubTypes.Type(ClassifyRequestTextOneOf.class),
            @JsonSubTypes.Type(ClassifyRequestTextOneOf1.class)
    })
    abstract static class ClassifyRequestTextMixin {
    }
}
