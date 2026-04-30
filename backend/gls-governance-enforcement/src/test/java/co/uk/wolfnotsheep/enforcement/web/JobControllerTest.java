package co.uk.wolfnotsheep.enforcement.web;

import co.uk.wolfnotsheep.enforcement.jobs.JobRecord;
import co.uk.wolfnotsheep.enforcement.jobs.JobState;
import co.uk.wolfnotsheep.enforcement.jobs.JobStore;
import co.uk.wolfnotsheep.enforcement.model.AppliedSummary;
import co.uk.wolfnotsheep.enforcement.model.EnforceResponse;
import co.uk.wolfnotsheep.enforcement.model.JobStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JobControllerTest {

    private JobStore jobs;
    private ObjectMapper mapper;
    private JobController controller;

    @BeforeEach
    void setUp() {
        jobs = mock(JobStore.class);
        mapper = new ObjectMapper().findAndRegisterModules();
        controller = new JobController(jobs, mapper);
    }

    @Test
    void unknown_nodeRunId_throws_JobNotFoundException() {
        when(jobs.find("missing")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> controller.getJob("missing"))
                .isInstanceOf(JobNotFoundException.class);
    }

    @Test
    void pending_row_returns_PENDING() {
        Instant now = Instant.now();
        when(jobs.find("p1")).thenReturn(Optional.of(
                new JobRecord("p1", JobState.PENDING, now, null, null, null, null, null,
                        now.plusSeconds(60))));

        JobStatus body = controller.getJob("p1").getBody();
        assertThat(controller.getJob("p1").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(body.getStatus()).isEqualTo(JobStatus.StatusEnum.PENDING);
        assertThat(body.getResult()).isNull();
    }

    @Test
    void completed_row_carries_deserialised_EnforceResponse() throws Exception {
        EnforceResponse cached = new EnforceResponse();
        cached.setNodeRunId("c1");
        cached.setDocumentId("doc-1");
        AppliedSummary applied = new AppliedSummary();
        applied.setAppliedPolicyIds(List.of("policy-uk-gdpr"));
        applied.setStorageMigrated(true);
        applied.setStorageTierBefore("tier-hot");
        applied.setStorageTierAfter("tier-cold");
        cached.setApplied(applied);
        cached.setDurationMs(120);
        String json = mapper.writeValueAsString(cached);
        Instant now = Instant.now();
        when(jobs.find("c1")).thenReturn(Optional.of(
                new JobRecord("c1", JobState.COMPLETED, now, now, now, json, null, null,
                        now.plusSeconds(60))));

        JobStatus body = controller.getJob("c1").getBody();
        assertThat(body.getStatus()).isEqualTo(JobStatus.StatusEnum.COMPLETED);
        assertThat(body.getResult().getNodeRunId()).isEqualTo("c1");
        assertThat(body.getResult().getDocumentId()).isEqualTo("doc-1");
        assertThat(body.getResult().getApplied().getStorageMigrated()).isTrue();
    }

    @Test
    void failed_row_carries_errorCode_and_errorMessage() {
        Instant now = Instant.now();
        when(jobs.find("f1")).thenReturn(Optional.of(
                new JobRecord("f1", JobState.FAILED, now, now, now, null,
                        "DOCUMENT_NOT_FOUND", "no document",
                        now.plusSeconds(60))));

        JobStatus body = controller.getJob("f1").getBody();
        assertThat(body.getStatus()).isEqualTo(JobStatus.StatusEnum.FAILED);
        assertThat(body.getErrorCode()).isEqualTo("DOCUMENT_NOT_FOUND");
        assertThat(body.getErrorMessage()).isEqualTo("no document");
    }
}
