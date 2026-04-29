package co.uk.wolfnotsheep.router.web;

import co.uk.wolfnotsheep.router.jobs.JobRecord;
import co.uk.wolfnotsheep.router.jobs.JobState;
import co.uk.wolfnotsheep.router.jobs.JobStore;
import co.uk.wolfnotsheep.router.model.ClassifyResponse;
import co.uk.wolfnotsheep.router.model.JobStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.Map;
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
        mapper = new ObjectMapper();
        controller = new JobController(jobs, mapper);
    }

    @Test
    void unknown_nodeRunId_throws_JobNotFoundException() {
        when(jobs.find("missing")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> controller.getJob("missing"))
                .isInstanceOf(JobNotFoundException.class)
                .hasMessageContaining("missing");
    }

    @Test
    void pending_row_returns_PENDING_with_no_result() {
        Instant now = Instant.now();
        when(jobs.find("p1")).thenReturn(Optional.of(
                new JobRecord("p1", JobState.PENDING, now, null, null, null, null, null,
                        now.plusSeconds(60))));

        ResponseEntity<JobStatus> resp = controller.getJob("p1");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        JobStatus body = resp.getBody();
        assertThat(body.getNodeRunId()).isEqualTo("p1");
        assertThat(body.getStatus()).isEqualTo(JobStatus.StatusEnum.PENDING);
        assertThat(body.getResult()).isNull();
        assertThat(body.getErrorCode()).isNull();
    }

    @Test
    void running_row_returns_RUNNING_with_startedAt() {
        Instant now = Instant.now();
        when(jobs.find("r1")).thenReturn(Optional.of(
                new JobRecord("r1", JobState.RUNNING, now, now, null, null, null, null,
                        now.plusSeconds(60))));

        JobStatus body = controller.getJob("r1").getBody();

        assertThat(body.getStatus()).isEqualTo(JobStatus.StatusEnum.RUNNING);
        assertThat(body.getStartedAt()).isNotNull();
        assertThat(body.getCompletedAt()).isNull();
    }

    @Test
    void completed_row_carries_the_deserialised_ClassifyResponse() throws Exception {
        ClassifyResponse cached = new ClassifyResponse();
        cached.setNodeRunId("c1");
        cached.setTierOfDecision(ClassifyResponse.TierOfDecisionEnum.MOCK);
        cached.setConfidence(0.5f);
        cached.setResult(Map.of("category", "MOCK_CATEGORY"));
        cached.setDurationMs(5);
        cached.setCostUnits(0);
        String json = mapper.writeValueAsString(cached);
        Instant now = Instant.now();
        when(jobs.find("c1")).thenReturn(Optional.of(
                new JobRecord("c1", JobState.COMPLETED, now, now, now, json, null, null,
                        now.plusSeconds(60))));

        JobStatus body = controller.getJob("c1").getBody();

        assertThat(body.getStatus()).isEqualTo(JobStatus.StatusEnum.COMPLETED);
        assertThat(body.getStartedAt()).isNotNull();
        assertThat(body.getCompletedAt()).isNotNull();
        assertThat(body.getResult()).isNotNull();
        assertThat(body.getResult().getNodeRunId()).isEqualTo("c1");
        assertThat(body.getResult().getTierOfDecision())
                .isEqualTo(ClassifyResponse.TierOfDecisionEnum.MOCK);
    }

    @Test
    void failed_row_carries_errorCode_and_errorMessage() {
        Instant now = Instant.now();
        when(jobs.find("f1")).thenReturn(Optional.of(
                new JobRecord("f1", JobState.FAILED, now, now, now, null,
                        "ROUTER_LLM_TIMEOUT", "LLM tier did not respond",
                        now.plusSeconds(60))));

        JobStatus body = controller.getJob("f1").getBody();

        assertThat(body.getStatus()).isEqualTo(JobStatus.StatusEnum.FAILED);
        assertThat(body.getErrorCode()).isEqualTo("ROUTER_LLM_TIMEOUT");
        assertThat(body.getErrorMessage()).isEqualTo("LLM tier did not respond");
        assertThat(body.getResult()).isNull();
    }
}
