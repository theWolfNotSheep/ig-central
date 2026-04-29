package co.uk.wolfnotsheep.router.web;

import co.uk.wolfnotsheep.router.api.ClassifyApi;
import co.uk.wolfnotsheep.router.idempotency.IdempotencyInFlightException;
import co.uk.wolfnotsheep.router.idempotency.IdempotencyOutcome;
import co.uk.wolfnotsheep.router.idempotency.IdempotencyStore;
import co.uk.wolfnotsheep.router.model.BlockRef;
import co.uk.wolfnotsheep.router.model.ClassifyRequest;
import co.uk.wolfnotsheep.router.model.ClassifyRequestTextOneOf;
import co.uk.wolfnotsheep.router.model.ClassifyResponse;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClassifyControllerTest {

    private IdempotencyStore idempotency;
    private ObjectMapper mapper;
    private AuditEmitter auditEmitter;
    private ClassifyController controller;

    @BeforeEach
    void setUp() {
        idempotency = mock(IdempotencyStore.class);
        when(idempotency.tryAcquire(any())).thenReturn(IdempotencyOutcome.acquired());
        mapper = new ObjectMapper();
        auditEmitter = mock(AuditEmitter.class);
        controller = new ClassifyController(
                new MockCascadeService(),
                idempotency,
                new ExtractMetrics(new SimpleMeterRegistry()),
                mapper,
                providerOf(auditEmitter),
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
                validTraceparent(), promptRequest("node-1", "block-1", "the document text"), null);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        ClassifyResponse body = resp.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getNodeRunId()).isEqualTo("node-1");
        assertThat(body.getTierOfDecision()).isEqualTo(ClassifyResponse.TierOfDecisionEnum.MOCK);
        assertThat(body.getConfidence()).isEqualTo(0.5f);
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) body.getResult();
        assertThat(result).containsEntry("category", "MOCK_CATEGORY");
    }

    @Test
    void successful_classify_emits_CLASSIFY_COMPLETED_tier_2_audit() {
        controller.classify(validTraceparent(),
                promptRequest("node-audit", "block-1", "txt"), null);

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
    void in_flight_idempotency_short_circuits() {
        when(idempotency.tryAcquire("node-busy")).thenReturn(IdempotencyOutcome.inFlight());

        assertThatThrownBy(() -> controller.classify(validTraceparent(),
                promptRequest("node-busy", "block-1", "x"), null))
                .isInstanceOf(IdempotencyInFlightException.class);

        verify(auditEmitter, never()).emit(any());
    }

    @Test
    void cached_idempotency_returns_stored_response() throws Exception {
        ClassifyResponse cached = new ClassifyResponse();
        cached.setNodeRunId("node-c");
        cached.setTierOfDecision(ClassifyResponse.TierOfDecisionEnum.MOCK);
        cached.setConfidence(0.5f);
        cached.setResult(Map.of("category", "PRE_CACHED"));
        cached.setDurationMs(0);
        cached.setCostUnits(0);
        String json = mapper.writeValueAsString(cached);
        when(idempotency.tryAcquire("node-c")).thenReturn(IdempotencyOutcome.cached(json));

        ResponseEntity<ClassifyResponse> resp = controller.classify(
                validTraceparent(), promptRequest("node-c", "block-1", "x"), null);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().getNodeRunId()).isEqualTo("node-c");
        verify(auditEmitter, never()).emit(any());
    }

    @Test
    void successful_classify_caches_the_response() {
        controller.classify(validTraceparent(),
                promptRequest("node-cache", "block-1", "x"), null);

        verify(idempotency, times(1)).cacheResult(eq("node-cache"), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void controller_implements_generated_api() {
        assertThat(ClassifyApi.class.isAssignableFrom(ClassifyController.class)).isTrue();
    }

    private static String eq(String s) {
        return org.mockito.ArgumentMatchers.eq(s);
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
