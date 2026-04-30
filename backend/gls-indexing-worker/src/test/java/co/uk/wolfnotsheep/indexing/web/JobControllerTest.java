package co.uk.wolfnotsheep.indexing.web;

import co.uk.wolfnotsheep.indexing.jobs.JobRecord;
import co.uk.wolfnotsheep.indexing.jobs.JobState;
import co.uk.wolfnotsheep.indexing.jobs.JobStore;
import co.uk.wolfnotsheep.indexing.model.JobStatus;
import co.uk.wolfnotsheep.indexing.model.ReindexSummary;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Instant;
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
    void pending_row_returns_PENDING_with_no_summary() {
        Instant now = Instant.now();
        when(jobs.find("p1")).thenReturn(Optional.of(
                new JobRecord("p1", JobState.PENDING, now, null, null, null, null, null,
                        now.plusSeconds(60))));

        var resp = controller.getJob("p1");
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().getStatus()).isEqualTo(JobStatus.StatusEnum.PENDING);
        assertThat(resp.getBody().getSummary()).isNull();
    }

    @Test
    void completed_row_carries_deserialised_summary() throws Exception {
        ReindexSummary cached = new ReindexSummary();
        cached.setTotalDocuments(100);
        cached.setIndexedCount(95);
        cached.setSkippedCount(3);
        cached.setFailedCount(2);
        cached.setDurationMs(1234);
        String json = mapper.writeValueAsString(cached);
        Instant now = Instant.now();
        when(jobs.find("c1")).thenReturn(Optional.of(
                new JobRecord("c1", JobState.COMPLETED, now, now, now, json, null, null,
                        now.plusSeconds(60))));

        JobStatus body = controller.getJob("c1").getBody();
        assertThat(body.getStatus()).isEqualTo(JobStatus.StatusEnum.COMPLETED);
        assertThat(body.getSummary().getTotalDocuments()).isEqualTo(100);
        assertThat(body.getSummary().getIndexedCount()).isEqualTo(95);
        assertThat(body.getSummary().getSkippedCount()).isEqualTo(3);
        assertThat(body.getSummary().getFailedCount()).isEqualTo(2);
    }

    @Test
    void failed_row_surfaces_errorCode_and_message() {
        Instant now = Instant.now();
        when(jobs.find("f1")).thenReturn(Optional.of(
                new JobRecord("f1", JobState.FAILED, now, now, now, null,
                        "INDEX_BACKEND_UNAVAILABLE", "ES down", now.plusSeconds(60))));

        JobStatus body = controller.getJob("f1").getBody();
        assertThat(body.getStatus()).isEqualTo(JobStatus.StatusEnum.FAILED);
        assertThat(body.getErrorCode()).isEqualTo("INDEX_BACKEND_UNAVAILABLE");
        assertThat(body.getErrorMessage()).isEqualTo("ES down");
    }
}
