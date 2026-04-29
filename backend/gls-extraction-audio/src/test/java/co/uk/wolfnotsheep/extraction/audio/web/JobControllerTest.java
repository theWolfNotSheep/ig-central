package co.uk.wolfnotsheep.extraction.audio.web;

import co.uk.wolfnotsheep.extraction.audio.api.JobsApi;
import co.uk.wolfnotsheep.extraction.audio.jobs.JobRecord;
import co.uk.wolfnotsheep.extraction.audio.jobs.JobState;
import co.uk.wolfnotsheep.extraction.audio.jobs.JobStore;
import co.uk.wolfnotsheep.extraction.audio.model.JobStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JobControllerTest {

    private final JobStore jobs = mock(JobStore.class);
    private final JobController controller = new JobController(jobs, new ObjectMapper());

    @Test
    void returns_404_when_job_does_not_exist() {
        when(jobs.find("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.getJob("nope"))
                .isInstanceOf(JobNotFoundException.class);
    }

    @Test
    void returns_pending_status_for_in_flight_job() {
        when(jobs.find("node-pending")).thenReturn(Optional.of(new JobRecord(
                "node-pending", JobState.PENDING, Instant.now(), null, null, null, null, null,
                Instant.now().plusSeconds(3600))));

        ResponseEntity<JobStatus> resp = controller.getJob("node-pending");

        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(resp.getBody().getStatus()).isEqualTo(JobStatus.StatusEnum.PENDING);
        assertThat(resp.getBody().getResult()).isNull();
    }

    @Test
    void returns_completed_status_with_result_for_completed_job() throws Exception {
        ObjectMapper m = new ObjectMapper();
        var inline = new co.uk.wolfnotsheep.extraction.audio.model.ExtractResponseTextOneOf();
        inline.setText("done");
        inline.setEncoding(co.uk.wolfnotsheep.extraction.audio.model.ExtractResponseTextOneOf.EncodingEnum.UTF_8);
        var resp = new co.uk.wolfnotsheep.extraction.audio.model.ExtractResponse();
        resp.setNodeRunId("node-done");
        resp.setText(inline);
        resp.setDetectedMimeType("audio/mpeg");
        resp.setDurationMs(123);
        resp.setCostUnits(1);
        String json = m.writeValueAsString(resp);
        when(jobs.find("node-done")).thenReturn(Optional.of(new JobRecord(
                "node-done", JobState.COMPLETED, Instant.now(), Instant.now(), Instant.now(),
                json, null, null, Instant.now().plusSeconds(3600))));

        ResponseEntity<JobStatus> response = controller.getJob("node-done");

        assertThat(response.getBody().getStatus()).isEqualTo(JobStatus.StatusEnum.COMPLETED);
        assertThat(response.getBody().getResult()).isNotNull();
        assertThat(response.getBody().getResult().getNodeRunId()).isEqualTo("node-done");
    }

    @Test
    void returns_failed_status_with_error_fields() {
        when(jobs.find("node-fail")).thenReturn(Optional.of(new JobRecord(
                "node-fail", JobState.FAILED, Instant.now(), Instant.now(), Instant.now(),
                null, "AUDIO_NOT_CONFIGURED", "no backend wired",
                Instant.now().plusSeconds(3600))));

        ResponseEntity<JobStatus> resp = controller.getJob("node-fail");

        assertThat(resp.getBody().getStatus()).isEqualTo(JobStatus.StatusEnum.FAILED);
        assertThat(resp.getBody().getErrorCode()).isEqualTo("AUDIO_NOT_CONFIGURED");
        assertThat(resp.getBody().getErrorMessage()).isEqualTo("no backend wired");
    }

    @Test
    void controller_implements_generated_api() {
        assertThat(JobsApi.class.isAssignableFrom(JobController.class)).isTrue();
    }
}
