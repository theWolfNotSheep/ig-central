package co.uk.wolfnotsheep.auditcollector.web;

import co.uk.wolfnotsheep.auditcollector.model.GetCapabilities200Response;
import co.uk.wolfnotsheep.auditcollector.model.HealthResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class MetaControllerTest {

    @Test
    void capabilities_advertise_both_tiers() {
        MetaController controller = new MetaController("gls-audit-collector", "1.2.3");

        GetCapabilities200Response body = controller.getCapabilities().getBody();

        assertThat(body.getService()).isEqualTo("gls-audit-collector");
        assertThat(body.getVersion()).isEqualTo("1.2.3");
        assertThat(body.getTiers()).containsExactly("AUDIT_TIER_1", "AUDIT_TIER_2");
    }

    @Test
    void health_returns_UP_with_200() {
        MetaController controller = new MetaController("gls-audit-collector", "0.0.1");

        var response = controller.getHealth();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getStatus()).isEqualTo(HealthResponse.StatusEnum.UP);
    }
}
