package co.uk.wolfnotsheep.router.web;

import co.uk.wolfnotsheep.router.api.ClassifyApi;
import co.uk.wolfnotsheep.router.jobs.JobAcquisition;
import co.uk.wolfnotsheep.router.jobs.JobRecord;
import co.uk.wolfnotsheep.router.jobs.JobState;
import co.uk.wolfnotsheep.router.jobs.JobStore;
import co.uk.wolfnotsheep.router.model.BlockRef;
import co.uk.wolfnotsheep.router.model.ClassifyRequest;
import co.uk.wolfnotsheep.router.model.ClassifyRequestTextOneOf;
import co.uk.wolfnotsheep.router.model.ClassifyResponse;
import co.uk.wolfnotsheep.router.model.JobAccepted;
import co.uk.wolfnotsheep.router.parse.MockCascadeService;
import co.uk.wolfnotsheep.platformaudit.emit.AuditEmitter;
import co.uk.wolfnotsheep.platformaudit.envelope.AuditEvent;
import co.uk.wolfnotsheep.platformaudit.envelope.Outcome;
import co.uk.wolfnotsheep.platformaudit.envelope.Tier;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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

    private JobStore jobs;
    private ObjectMapper mapper;
    private AuditEmitter auditEmitter;
    private AsyncDispatcher asyncDispatcher;
    private ClassifyController controller;

    @BeforeEach
    void setUp() {
        jobs = mock(JobStore.class);
        when(jobs.tryAcquire(any())).thenReturn(JobAcquisition.acquired());
        mapper = new ObjectMapper();
        auditEmitter = mock(AuditEmitter.class);
        asyncDispatcher = mock(AsyncDispatcher.class);
        controller = new ClassifyController(
                new MockCascadeService(),
                jobs,
                new ExtractMetrics(new SimpleMeterRegistry()),
                mapper,
                providerOf(auditEmitter),
                asyncDispatcher,
                "gls-classifier-router", "0.0.1-SNAPSHOT", "test-instance");
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<AuditEmitter> providerOf(AuditEmitter emitter) {
        ObjectProvider<AuditEmitter> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(emitter);
        return provider;
    }

    @Test
    void happy_path_returns_200_with_MOCK_tier() {
        ResponseEntity<ClassifyResponse> resp = controller.classify(
                validTraceparent(), promptRequest("node-1", "block-1", "the document text"),
                null, null);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        ClassifyResponse body = resp.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getNodeRunId()).isEqualTo("node-1");
        assertThat(body.getTierOfDecision()).isEqualTo(ClassifyResponse.TierOfDecisionEnum.MOCK);
        assertThat(body.getConfidence()).isEqualTo(0.5f);
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) body.getResult();
        assertThat(result).containsEntry("category", "MOCK_CATEGORY");
        verify(jobs, times(1)).markRunning("node-1");
        verify(jobs, times(1)).markCompleted(eq("node-1"), anyString());
    }

    @Test
    void successful_classify_emits_CLASSIFY_COMPLETED_tier_2_audit() {
        controller.classify(validTraceparent(),
                promptRequest("node-audit", "block-1", "txt"), null, null);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEmitter, times(1)).emit(captor.capture());
        AuditEvent emitted = captor.getValue();
        assertThat(emitted.eventType()).isEqualTo("CLASSIFY_COMPLETED");
        assertThat(emitted.tier()).isEqualTo(Tier.SYSTEM);
        assertThat(emitted.outcome()).isEqualTo(Outcome.SUCCESS);
        assertThat(emitted.action()).isEqualTo("CLASSIFY");
        assertThat(emitted.nodeRunId()).isEqualTo("node-audit");
        assertThat(emitted.details().metadata()).containsEntry("blockId", "block-1");
        assertThat(emitted.details().metadata()).containsEntry("tierOfDecision", "MOCK");
    }

    @Test
    void in_flight_idempotency_short_circuits_sync() {
        when(jobs.tryAcquire("node-busy")).thenReturn(
                JobAcquisition.running(pendingRow("node-busy")));

        assertThatThrownBy(() -> controller.classify(validTraceparent(),
                promptRequest("node-busy", "block-1", "x"), null, null))
                .isInstanceOf(JobInFlightException.class);

        verify(auditEmitter, never()).emit(any());
        verify(asyncDispatcher, never()).dispatch(any(), any());
    }

    @Test
    void cached_idempotency_returns_stored_response_sync() throws Exception {
        ClassifyResponse cached = new ClassifyResponse();
        cached.setNodeRunId("node-c");
        cached.setTierOfDecision(ClassifyResponse.TierOfDecisionEnum.MOCK);
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
        verify(auditEmitter, never()).emit(any());
        verify(jobs, never()).markRunning(any());
    }

    @Test
    void successful_classify_caches_the_response() {
        controller.classify(validTraceparent(),
                promptRequest("node-cache", "block-1", "x"), null, null);

        verify(jobs, times(1)).markCompleted(eq("node-cache"), anyString());
    }

    @Test
    void respond_async_returns_202_with_Location_and_dispatches_in_background() {
        ResponseEntity<ClassifyResponse> resp = controller.classify(
                validTraceparent(), promptRequest("node-async", "block-1", "the doc"),
                null, "respond-async");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(resp.getHeaders().getFirst(HttpHeaders.LOCATION))
                .isEqualTo("/v1/jobs/node-async");
        // Body is a JobAccepted; the generated return type is narrowed
        // to ClassifyResponse, so we read it via Object.
        Object body = resp.getBody();
        assertThat(body).isInstanceOf(JobAccepted.class);
        JobAccepted accepted = (JobAccepted) body;
        assertThat(accepted.getNodeRunId()).isEqualTo("node-async");
        assertThat(accepted.getStatus()).isEqualTo(JobAccepted.StatusEnum.PENDING);

        verify(asyncDispatcher, times(1)).dispatch(any(ClassifyRequest.class), any());
        // Sync side-effects are not invoked on the dispatch thread.
        verify(jobs, never()).markRunning(any());
        verify(jobs, never()).markCompleted(any(), any());
    }

    @Test
    void respond_async_after_completed_run_returns_202_pointing_at_existing_row() {
        when(jobs.tryAcquire("node-cached-async")).thenReturn(
                JobAcquisition.completed(completedRow("node-cached-async", "{}")));

        ResponseEntity<ClassifyResponse> resp = controller.classify(
                validTraceparent(),
                promptRequest("node-cached-async", "block-1", "x"),
                null, "respond-async");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(resp.getHeaders().getFirst(HttpHeaders.LOCATION))
                .isEqualTo("/v1/jobs/node-cached-async");
        // The existing row already holds the cached result; no
        // re-dispatch.
        verify(asyncDispatcher, never()).dispatch(any(), any());
    }

    @Test
    void respond_async_with_running_row_returns_202_without_redispatch() {
        when(jobs.tryAcquire("node-running")).thenReturn(
                JobAcquisition.running(pendingRow("node-running")));

        ResponseEntity<ClassifyResponse> resp = controller.classify(
                validTraceparent(),
                promptRequest("node-running", "block-1", "x"),
                null, "respond-async");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        verify(asyncDispatcher, never()).dispatch(any(), any());
    }

    @Test
    void controller_implements_generated_api() {
        assertThat(ClassifyApi.class.isAssignableFrom(ClassifyController.class)).isTrue();
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
        BlockRef block = new BlockRef();
        block.setId(blockId);
        block.setType(BlockRef.TypeEnum.PROMPT);
        ClassifyRequestTextOneOf inline = new ClassifyRequestTextOneOf();
        inline.setText(text);
        inline.setEncoding("utf-8");
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
