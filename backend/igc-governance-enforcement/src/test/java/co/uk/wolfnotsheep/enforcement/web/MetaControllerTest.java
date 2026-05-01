package co.uk.wolfnotsheep.enforcement.web;

import co.uk.wolfnotsheep.enforcement.model.GetCapabilities200Response;
import co.uk.wolfnotsheep.enforcement.model.HealthResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class MetaControllerTest {

    @Test
    void capabilities_advertises_enforcement_tier_and_service_metadata() {
        MetaController controller = new MetaController("igc-enforcement-worker", "1.2.3");

        GetCapabilities200Response body = controller.getCapabilities().getBody();

        assertThat(body.getService()).isEqualTo("igc-enforcement-worker");
        assertThat(body.getVersion()).isEqualTo("1.2.3");
        assertThat(body.getTiers()).containsExactly("ENFORCEMENT");
        assertThat(body.getModels()).isEmpty();
    }

    @Test
    void health_returns_UP_with_200() {
        MetaController controller = new MetaController("igc-enforcement-worker", "0.0.1");

        var response = controller.getHealth();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getStatus()).isEqualTo(HealthResponse.StatusEnum.UP);
    }
}
