package co.uk.wolfnotsheep.extraction.tika.web;

import co.uk.wolfnotsheep.extraction.tika.api.MetaApi;
import co.uk.wolfnotsheep.extraction.tika.model.GetCapabilities200Response;
import co.uk.wolfnotsheep.extraction.tika.model.HealthResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Implements {@code GET /v1/capabilities} and {@code GET /actuator/health}
 * from {@code contracts/extraction/openapi.yaml}.
 *
 * <p>Capabilities returns this build's identity ({@code service},
 * {@code version}) and the audited supports — pure extraction container,
 * no inference models loaded, single tier ({@code default}). Health is a
 * minimal {@code UP} probe; component-level health (Tika init, MinIO
 * reach) is exposed under Spring Boot Actuator's
 * {@code /actuator/health/{component}} path when the relevant
 * {@code HealthIndicator} beans are wired.
 */
@RestController
public class MetaController implements MetaApi {

    private final String serviceName;
    private final String serviceVersion;

    public MetaController(
            @Value("${spring.application.name:gls-extraction-tika}") String serviceName,
            @Value("${gls.extraction.build.version:0.0.1-SNAPSHOT}") String serviceVersion) {
        this.serviceName = serviceName;
        this.serviceVersion = serviceVersion;
    }

    @Override
    public ResponseEntity<GetCapabilities200Response> getCapabilities() {
        GetCapabilities200Response body = new GetCapabilities200Response();
        body.setService(serviceName);
        body.setVersion(serviceVersion);
        body.setTiers(List.of("default"));
        body.setModels(List.of());
        return ResponseEntity.ok(body);
    }

    @Override
    public ResponseEntity<HealthResponse> getHealth() {
        // Spring Boot Actuator's /actuator/health is the canonical
        // health probe. This implementation exists because the contract
        // declares the operation; routes are typically reverse-proxied
        // to the actuator endpoint in production. Returns UP when the
        // app reaches this code path — readiness checks for Tika /
        // MinIO live on the actuator side.
        HealthResponse body = new HealthResponse();
        body.setStatus(HealthResponse.StatusEnum.UP);
        return ResponseEntity.ok(body);
    }
}
