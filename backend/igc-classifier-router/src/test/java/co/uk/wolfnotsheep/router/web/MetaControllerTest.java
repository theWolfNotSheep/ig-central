package co.uk.wolfnotsheep.router.web;

import co.uk.wolfnotsheep.router.api.MetaApi;
import co.uk.wolfnotsheep.router.model.GetCapabilities200Response;
import co.uk.wolfnotsheep.router.model.HealthResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class MetaControllerTest {

    @Test
    void capabilities_advertises_service_identity_and_MOCK_tier() {
        MetaController controller = new MetaController("igc-classifier-router", "0.0.1-SNAPSHOT");

        ResponseEntity<GetCapabilities200Response> resp = controller.getCapabilities();

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        GetCapabilities200Response body = resp.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getService()).isEqualTo("igc-classifier-router");
        assertThat(body.getVersion()).isEqualTo("0.0.1-SNAPSHOT");
        assertThat(body.getTiers()).containsExactly("MOCK");
    }

    @Test
    void health_returns_UP() {
        MetaController controller = new MetaController("igc-classifier-router", "0.0.1");

        ResponseEntity<HealthResponse> resp = controller.getHealth();

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().getStatus()).isEqualTo(HealthResponse.StatusEnum.UP);
    }

    @Test
    void controller_implements_generated_meta_api() {
        assertThat(MetaApi.class.isAssignableFrom(MetaController.class)).isTrue();
    }
}
