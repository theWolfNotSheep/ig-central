package co.uk.wolfnotsheep.bert.web;

import co.uk.wolfnotsheep.bert.api.InferenceApi;
import co.uk.wolfnotsheep.bert.inference.InferenceEngine;
import co.uk.wolfnotsheep.bert.inference.InferenceResult;
import co.uk.wolfnotsheep.bert.inference.ModelNotLoadedException;
import co.uk.wolfnotsheep.bert.inference.NotLoadedInferenceEngine;
import co.uk.wolfnotsheep.bert.model.BertBlockRef;
import co.uk.wolfnotsheep.bert.model.InferRequest;
import co.uk.wolfnotsheep.bert.model.InferRequestTextOneOf;
import co.uk.wolfnotsheep.bert.model.InferResponse;
import co.uk.wolfnotsheep.platformaudit.emit.AuditEmitter;
import co.uk.wolfnotsheep.platformaudit.envelope.AuditEvent;
import co.uk.wolfnotsheep.platformaudit.envelope.Outcome;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InferControllerTest {

    private InferenceEngine engine;
    private AuditEmitter auditEmitter;
    private InferController controller;

    @BeforeEach
    void setUp() {
        engine = mock(InferenceEngine.class);
        auditEmitter = mock(AuditEmitter.class);
        controller = new InferController(
                engine,
                new InferMetrics(new SimpleMeterRegistry()),
                providerOf(auditEmitter),
                "gls-bert-inference", "0.0.1-SNAPSHOT", "test-instance");
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<AuditEmitter> providerOf(AuditEmitter emitter) {
        ObjectProvider<AuditEmitter> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(emitter);
        return provider;
    }

    @Test
    void happy_path_returns_200_with_inference_result() {
        when(engine.infer(any(), any(), any()))
                .thenReturn(new InferenceResult(
                        "hr_letter", 0.94f,
                        List.of(new InferenceResult.LabelScore("hr_letter", 0.94f),
                                new InferenceResult.LabelScore("finance_invoice", 0.06f)),
                        "2026.04.0",
                        2048L));

        ResponseEntity<InferResponse> resp = controller.infer(
                validTraceparent(), request("block-1", 3, "node-x", "the document text"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        InferResponse body = resp.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getLabel()).isEqualTo("hr_letter");
        assertThat(body.getConfidence()).isEqualTo(0.94f);
        assertThat(body.getModelVersion()).isEqualTo("2026.04.0");
        assertThat(body.getScores()).hasSize(2);
        assertThat(body.getCostUnits()).isEqualTo(2);
    }

    @Test
    void model_not_loaded_propagates_for_handler_to_map_to_503() {
        InferenceEngine notLoaded = new NotLoadedInferenceEngine();
        InferController c = new InferController(
                notLoaded,
                new InferMetrics(new SimpleMeterRegistry()),
                providerOf(auditEmitter),
                "gls-bert-inference", "0.0.1", "test");

        assertThatThrownBy(() -> c.infer(validTraceparent(),
                request("block-1", null, "n", "x")))
                .isInstanceOf(ModelNotLoadedException.class);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEmitter, times(1)).emit(captor.capture());
        assertThat(captor.getValue().outcome()).isEqualTo(Outcome.FAILURE);
        assertThat(captor.getValue().details().metadata())
                .containsEntry("errorCode", "MODEL_NOT_LOADED");
    }

    @Test
    void successful_infer_emits_INFER_COMPLETED_audit() {
        when(engine.infer(any(), any(), any()))
                .thenReturn(new InferenceResult("ok", 0.7f, List.of(), "v1", 100L));

        controller.infer(validTraceparent(), request("block-1", null, "n", "x"));

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEmitter, times(1)).emit(captor.capture());
        assertThat(captor.getValue().eventType()).isEqualTo("INFER_COMPLETED");
        assertThat(captor.getValue().action()).isEqualTo("INFER");
        assertThat(captor.getValue().details().metadata()).containsEntry("modelVersion", "v1");
    }

    @Test
    void controller_implements_generated_api() {
        assertThat(InferenceApi.class.isAssignableFrom(InferController.class)).isTrue();
    }

    private static InferRequest request(String blockId, Integer blockVersion,
                                        String nodeRunId, String text) {
        BertBlockRef block = new BertBlockRef();
        block.setId(blockId);
        if (blockVersion != null) block.setVersion(blockVersion);
        InferRequestTextOneOf inline = new InferRequestTextOneOf();
        inline.setText(text);
        inline.setEncoding(InferRequestTextOneOf.EncodingEnum.UTF_8);
        InferRequest req = new InferRequest();
        req.setBlock(block);
        req.setText(inline);
        req.setNodeRunId(nodeRunId);
        return req;
    }

    private static String validTraceparent() {
        return "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01";
    }
}
