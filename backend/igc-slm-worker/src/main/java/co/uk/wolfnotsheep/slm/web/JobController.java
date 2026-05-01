package co.uk.wolfnotsheep.slm.web;

import co.uk.wolfnotsheep.slm.api.JobsApi;
import co.uk.wolfnotsheep.slm.jobs.JobRecord;
import co.uk.wolfnotsheep.slm.jobs.JobStore;
import co.uk.wolfnotsheep.slm.model.ClassifyRequestText;
import co.uk.wolfnotsheep.slm.model.ClassifyRequestTextOneOf;
import co.uk.wolfnotsheep.slm.model.ClassifyRequestTextOneOf1;
import co.uk.wolfnotsheep.slm.model.ClassifyResponse;
import co.uk.wolfnotsheep.slm.model.JobStatus;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
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
