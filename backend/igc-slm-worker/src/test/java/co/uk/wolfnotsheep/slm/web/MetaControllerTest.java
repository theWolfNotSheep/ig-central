package co.uk.wolfnotsheep.slm.web;

import co.uk.wolfnotsheep.slm.backend.NotConfiguredSlmService;
import co.uk.wolfnotsheep.slm.backend.SlmBackendId;
import co.uk.wolfnotsheep.slm.backend.SlmService;
import co.uk.wolfnotsheep.slm.model.GetCapabilities200Response;
import co.uk.wolfnotsheep.slm.model.HealthResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MetaControllerTest {

    @Test
    void capabilities_reports_SLM_tier_with_no_models_when_unconfigured() {
        MetaController controller = new MetaController(
                "igc-slm-worker", "0.0.1", new NotConfiguredSlmService());
        GetCapabilities200Response body = controller.getCapabilities().getBody();
        assertThat(body.getService()).isEqualTo("igc-slm-worker");
        assertThat(body.getVersion()).isEqualTo("0.0.1");
        assertThat(body.getTiers()).containsExactly("SLM");
        assertThat(body.getModels()).isEmpty();
    }

    @Test
    void capabilities_lists_active_backend_when_configured() {
        SlmService backend = mock(SlmService.class);
        when(backend.activeBackend()).thenReturn(SlmBackendId.ANTHROPIC_HAIKU);

        MetaController controller = new MetaController("svc", "v", backend);
        GetCapabilities200Response body = controller.getCapabilities().getBody();
        assertThat(body.getModels()).hasSize(1);
        assertThat(body.getModels().get(0).getName()).isEqualTo("ANTHROPIC_HAIKU");
    }

    @Test
    void health_503_OUT_OF_SERVICE_when_unready() {
        MetaController controller = new MetaController(
                "svc", "v", new NotConfiguredSlmService());
        ResponseEntity<HealthResponse> resp = controller.getHealth();
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(resp.getBody().getStatus()).isEqualTo(HealthResponse.StatusEnum.OUT_OF_SERVICE);
    }

    @Test
    void health_200_UP_when_backend_ready() {
        SlmService backend = mock(SlmService.class);
        when(backend.isReady()).thenReturn(true);

        MetaController controller = new MetaController("svc", "v", backend);
        ResponseEntity<HealthResponse> resp = controller.getHealth();
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().getStatus()).isEqualTo(HealthResponse.StatusEnum.UP);
    }
}
