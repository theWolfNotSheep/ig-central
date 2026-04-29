package co.uk.wolfnotsheep.bert.web;

import co.uk.wolfnotsheep.bert.api.ModelsApi;
import co.uk.wolfnotsheep.bert.model.ModelsResponse;
import co.uk.wolfnotsheep.bert.model.ReloadAccepted;
import co.uk.wolfnotsheep.bert.registry.LoadedModel;
import co.uk.wolfnotsheep.bert.registry.ModelRegistry;
import co.uk.wolfnotsheep.bert.registry.ReloadCoordinator;
import co.uk.wolfnotsheep.bert.registry.ReloadInProgressException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModelsControllerTest {

    @Test
    void list_models_returns_loaded_artefacts() {
        ModelRegistry registry = new ModelRegistry();
        registry.replace(List.of(new LoadedModel(
                "2026.04.0", "gls-bert-artifacts", "v0/2026.04.0.onnx",
                List.of("hr_letter", "finance_invoice"),
                List.of("block-hr"),
                Instant.now())));

        ModelsController controller = new ModelsController(registry, new ReloadCoordinator());
        ResponseEntity<ModelsResponse> resp = controller.listModels();

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().getModels()).hasSize(1);
        assertThat(resp.getBody().getModels().get(0).getModelVersion()).isEqualTo("2026.04.0");
        assertThat(resp.getBody().getModels().get(0).getLabels())
                .containsExactly("hr_letter", "finance_invoice");
    }

    @Test
    void list_models_returns_empty_when_no_models_loaded() {
        ModelsController controller = new ModelsController(
                new ModelRegistry(), new ReloadCoordinator());
        ResponseEntity<ModelsResponse> resp = controller.listModels();

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().getModels()).isEmpty();
    }

    @Test
    void reload_returns_202_with_PENDING_status() {
        ModelsController controller = new ModelsController(
                new ModelRegistry(), new ReloadCoordinator());
        ResponseEntity<ReloadAccepted> resp = controller.reloadModels(
                "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(resp.getBody().getStatus()).isEqualTo(ReloadAccepted.StatusEnum.PENDING);
        assertThat(resp.getBody().getStartedAt()).isNotNull();
    }

    @Test
    void reload_when_already_in_flight_throws() {
        ReloadCoordinator coordinator = new ReloadCoordinator();
        coordinator.beginReload();
        ModelsController controller = new ModelsController(new ModelRegistry(), coordinator);
        assertThatThrownBy(() -> controller.reloadModels("00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01"))
                .isInstanceOf(ReloadInProgressException.class);
    }

    @Test
    void controller_implements_generated_api() {
        assertThat(ModelsApi.class.isAssignableFrom(ModelsController.class)).isTrue();
    }
}
