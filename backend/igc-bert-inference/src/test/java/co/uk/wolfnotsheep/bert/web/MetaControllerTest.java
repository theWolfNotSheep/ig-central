package co.uk.wolfnotsheep.bert.web;

import co.uk.wolfnotsheep.bert.api.MetaApi;
import co.uk.wolfnotsheep.bert.inference.InferenceEngine;
import co.uk.wolfnotsheep.bert.inference.NotLoadedInferenceEngine;
import co.uk.wolfnotsheep.bert.model.GetCapabilities200Response;
import co.uk.wolfnotsheep.bert.model.HealthResponse;
import co.uk.wolfnotsheep.bert.registry.LoadedModel;
import co.uk.wolfnotsheep.bert.registry.ModelRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MetaControllerTest {

    @Test
    void capabilities_lists_BERT_tier_and_loaded_models() {
        ModelRegistry registry = new ModelRegistry();
        registry.replace(List.of(new LoadedModel(
                "2026.04.0", "b", "k", List.of("hr"), List.of("block-hr"), Instant.now())));
        InferenceEngine engine = mock(InferenceEngine.class);
        when(engine.isReady()).thenReturn(true);

        MetaController controller = new MetaController(
                "igc-bert-inference", "0.0.1-SNAPSHOT", engine, registry);

        ResponseEntity<GetCapabilities200Response> resp = controller.getCapabilities();

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().getTiers()).containsExactly("BERT");
        assertThat(resp.getBody().getModels()).hasSize(1);
        assertThat(resp.getBody().getModels().get(0).getName()).isEqualTo("BERT");
        assertThat(resp.getBody().getModels().get(0).getVersion()).isEqualTo("2026.04.0");
    }

    @Test
    void health_returns_OUT_OF_SERVICE_503_when_no_model_loaded() {
        MetaController controller = new MetaController(
                "igc-bert-inference", "0.0.1",
                new NotLoadedInferenceEngine(),
                new ModelRegistry());

        ResponseEntity<HealthResponse> resp = controller.getHealth();

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(resp.getBody().getStatus())
                .isEqualTo(HealthResponse.StatusEnum.OUT_OF_SERVICE);
    }

    @Test
    void health_returns_UP_when_engine_ready_and_models_present() {
        ModelRegistry registry = new ModelRegistry();
        registry.replace(List.of(new LoadedModel(
                "v1", "b", "k", List.of("a"), List.of("block-a"), Instant.now())));
        InferenceEngine engine = mock(InferenceEngine.class);
        when(engine.isReady()).thenReturn(true);

        MetaController controller = new MetaController(
                "igc-bert-inference", "0.0.1", engine, registry);

        ResponseEntity<HealthResponse> resp = controller.getHealth();

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().getStatus()).isEqualTo(HealthResponse.StatusEnum.UP);
    }

    @Test
    void controller_implements_generated_meta_api() {
        assertThat(MetaApi.class.isAssignableFrom(MetaController.class)).isTrue();
    }
}
