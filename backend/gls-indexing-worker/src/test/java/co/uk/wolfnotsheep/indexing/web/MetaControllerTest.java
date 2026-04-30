package co.uk.wolfnotsheep.indexing.web;

import co.uk.wolfnotsheep.indexing.model.GetCapabilities200Response;
import co.uk.wolfnotsheep.indexing.model.HealthResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class MetaControllerTest {

    @Test
    void capabilities_advertise_indexing_tier() {
        MetaController controller = new MetaController("gls-indexing-worker", "1.2.3");

        GetCapabilities200Response body = controller.getCapabilities().getBody();

        assertThat(body.getService()).isEqualTo("gls-indexing-worker");
        assertThat(body.getVersion()).isEqualTo("1.2.3");
        assertThat(body.getTiers()).containsExactly("INDEXING");
    }

    @Test
    void health_returns_UP_with_200() {
        MetaController controller = new MetaController("gls-indexing-worker", "0.0.1");

        var response = controller.getHealth();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getStatus()).isEqualTo(HealthResponse.StatusEnum.UP);
    }
}
