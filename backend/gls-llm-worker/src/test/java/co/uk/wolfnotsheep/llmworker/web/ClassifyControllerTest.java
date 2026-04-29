package co.uk.wolfnotsheep.llmworker.web;

import co.uk.wolfnotsheep.llmworker.api.ClassifyApi;
import co.uk.wolfnotsheep.llmworker.backend.NotConfiguredLlmService;
import co.uk.wolfnotsheep.llmworker.backend.LlmBackendId;
import co.uk.wolfnotsheep.llmworker.backend.LlmResult;
import co.uk.wolfnotsheep.llmworker.backend.LlmService;
import co.uk.wolfnotsheep.llmworker.jobs.JobAcquisition;
import co.uk.wolfnotsheep.llmworker.jobs.JobRecord;
import co.uk.wolfnotsheep.llmworker.jobs.JobState;
import co.uk.wolfnotsheep.llmworker.jobs.JobStore;
import co.uk.wolfnotsheep.llmworker.model.ClassifyRequest;
import co.uk.wolfnotsheep.llmworker.model.ClassifyRequestTextOneOf;
import co.uk.wolfnotsheep.llmworker.model.ClassifyResponse;
import co.uk.wolfnotsheep.llmworker.model.JobAccepted;
import co.uk.wolfnotsheep.llmworker.model.PromptBlockRef;
import co.uk.wolfnotsheep.platformaudit.emit.AuditEmitter;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClassifyControllerTest {

    private LlmService slm;
    private JobStore jobs;
    private ObjectMapper mapper;
    private AuditEmitter auditEmitter;
    private AsyncDispatcher asyncDispatcher;
    private ClassifyController controller;

    @BeforeEach
    void setUp() {
        slm = mock(LlmService.class);
        jobs = mock(JobStore.class);
        when(jobs.tryAcquire(any())).thenReturn(JobAcquisition.acquired());
        mapper = new ObjectMapper();
        auditEmitter = mock(AuditEmitter.class);
        asyncDispatcher = mock(AsyncDispatcher.class);
        controller = new ClassifyController(
                slm, jobs,
                new ClassifyMetrics(new SimpleMeterRegistry()),
                mapper,
                providerOf(auditEmitter),
                asyncDispatcher,
                "gls-llm-worker", "0.0.1-SNAPSHOT", "test-instance");
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<AuditEmitter> providerOf(AuditEmitter emitter) {
        ObjectProvider<AuditEmitter> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(emitter);
        return provider;
    }

    @Test
    void controller_implements_generated_api() {
        assertThat(ClassifyApi.class.isAssignableFrom(ClassifyController.class)).isTrue();
    }

    @Test
    void happy_path_returns_200_with_backend_and_caches() {
        when(slm.classify(any(), any(), any())).thenReturn(new LlmResult(
                Map.of("category", "HR Letter", "sensitivity", "INTERNAL"),
                0.81f, "looks like an HR letter",
                LlmBackendId.ANTHROPIC, "claude-sonnet-4-5",
                /* tokensIn */ 320, /* tokensOut */ 40));

        ResponseEntity<ClassifyResponse> resp = controller.classify(
                validTraceparent(), promptRequest("node-1", "block-1", "the doc"),
                null, null);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        ClassifyResponse body = resp.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getNodeRunId()).isEqualTo("node-1");
        // LLM ClassifyResponse has no backend field (only one provider)
        assertThat(body.getModelId()).isEqualTo("claude-sonnet-4-5");
        assertThat(body.getConfidence()).isEqualTo(0.81f);
        assertThat(body.getTokensIn()).isEqualTo(320);
        assertThat(body.getTokensOut()).isEqualTo(40);
        // (320 + 40 + 999) / 1000 = 1
        assertThat(body.getCostUnits()).isEqualTo(1);

        verify(jobs, times(1)).markRunning("node-1");
        verify(jobs, times(1)).markCompleted(eq("node-1"), anyString());
    }

    @Test
    void backend_failure_marks_job_failed_and_propagates() {
        when(slm.classify(any(), any(), any())).thenAnswer(inv -> {
            throw new co.uk.wolfnotsheep.llmworker.backend.LlmNotConfiguredException("no backend wired");
        });

        assertThatThrownBy(() -> controller.classify(
                validTraceparent(), promptRequest("node-fail", "block-1", "x"),
                null, null))
                .isInstanceOf(co.uk.wolfnotsheep.llmworker.backend.LlmNotConfiguredException.class);

        verify(jobs, times(1)).markFailed(
                eq("node-fail"), eq("LLM_NOT_CONFIGURED"), anyString());
    }

    @Test
    void in_flight_idempotency_short_circuits_sync() {
        when(jobs.tryAcquire("node-busy")).thenReturn(
                JobAcquisition.running(pendingRow("node-busy")));

        assertThatThrownBy(() -> controller.classify(validTraceparent(),
                promptRequest("node-busy", "block-1", "x"), null, null))
                .isInstanceOf(JobInFlightException.class);

        verify(slm, never()).classify(any(), any(), any());
        verify(asyncDispatcher, never()).dispatch(any(), any());
    }

    @Test
    void cached_idempotency_returns_stored_response_sync() throws Exception {
        ClassifyResponse cached = new ClassifyResponse();
        cached.setNodeRunId("node-c");
        // no backend field
        cached.setConfidence(0.5f);
        cached.setResult(Map.of("category", "PRE_CACHED"));
        cached.setDurationMs(0);
        cached.setCostUnits(0);
        String json = mapper.writeValueAsString(cached);
        when(jobs.tryAcquire("node-c")).thenReturn(
                JobAcquisition.completed(completedRow("node-c", json)));

        ResponseEntity<ClassifyResponse> resp = controller.classify(
                validTraceparent(), promptRequest("node-c", "block-1", "x"), null, null);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().getNodeRunId()).isEqualTo("node-c");
        verify(slm, never()).classify(any(), any(), any());
        verify(jobs, never()).markRunning(any());
    }

    @Test
    void respond_async_returns_202_with_Location_and_dispatches() {
        ResponseEntity<ClassifyResponse> resp = controller.classify(
                validTraceparent(), promptRequest("node-async", "block-1", "the doc"),
                null, "respond-async");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(resp.getHeaders().getFirst(HttpHeaders.LOCATION))
                .isEqualTo("/v1/jobs/node-async");
        Object body = resp.getBody();
        assertThat(body).isInstanceOf(JobAccepted.class);
        JobAccepted accepted = (JobAccepted) body;
        assertThat(accepted.getNodeRunId()).isEqualTo("node-async");
        assertThat(accepted.getStatus()).isEqualTo(JobAccepted.StatusEnum.PENDING);

        verify(asyncDispatcher, times(1)).dispatch(any(ClassifyRequest.class), any());
        verify(jobs, never()).markRunning(any());
        verify(slm, never()).classify(any(), any(), any());
    }

    @Test
    void async_with_running_row_returns_202_without_redispatch() {
        when(jobs.tryAcquire("node-running")).thenReturn(
                JobAcquisition.running(pendingRow("node-running")));

        ResponseEntity<ClassifyResponse> resp = controller.classify(
                validTraceparent(), promptRequest("node-running", "block-1", "x"),
                null, "respond-async");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        verify(asyncDispatcher, never()).dispatch(any(), any());
    }

    @Test
    void slm_not_configured_real_stub_propagates_correct_errorCode() {
        // Sanity check that the real NotConfiguredLlmService throws
        // the right exception, hitting the same code path the mocked
        // failure path covers above.
        LlmService realStub = new NotConfiguredLlmService();
        ClassifyController withStub = new ClassifyController(
                realStub, jobs,
                new ClassifyMetrics(new SimpleMeterRegistry()),
                mapper,
                providerOf(auditEmitter),
                asyncDispatcher,
                "gls-llm-worker", "0.0.1-SNAPSHOT", "test-instance");

        assertThatThrownBy(() -> withStub.classify(
                validTraceparent(), promptRequest("node-stub", "block-1", "x"),
                null, null))
                .isInstanceOf(co.uk.wolfnotsheep.llmworker.backend.LlmNotConfiguredException.class);
    }

    private static String eq(String s) {
        return org.mockito.ArgumentMatchers.eq(s);
    }

    private static JobRecord pendingRow(String nodeRunId) {
        return new JobRecord(nodeRunId, JobState.PENDING, Instant.now(),
                null, null, null, null, null,
                Instant.now().plusSeconds(60));
    }

    private static JobRecord completedRow(String nodeRunId, String json) {
        Instant now = Instant.now();
        return new JobRecord(nodeRunId, JobState.COMPLETED, now, now, now, json, null, null,
                now.plusSeconds(60));
    }

    private static ClassifyRequest promptRequest(String nodeRunId, String blockId, String text) {
        PromptBlockRef block = new PromptBlockRef();
        block.setId(blockId);
        ClassifyRequestTextOneOf inline = new ClassifyRequestTextOneOf();
        inline.setText(text);
        inline.setEncoding(ClassifyRequestTextOneOf.EncodingEnum.UTF_8);
        ClassifyRequest req = new ClassifyRequest();
        req.setNodeRunId(nodeRunId);
        req.setBlock(block);
        req.setText(inline);
        return req;
    }

    private static String validTraceparent() {
        return "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01";
    }
}
