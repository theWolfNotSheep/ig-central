package co.uk.wolfnotsheep.router.web;

import co.uk.wolfnotsheep.router.api.MetaApi;
import co.uk.wolfnotsheep.router.model.GetCapabilities200Response;
import co.uk.wolfnotsheep.router.model.HealthResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class MetaController implements MetaApi {

    private final String serviceName;
    private final String serviceVersion;

    public MetaController(
            @Value("${spring.application.name:igc-classifier-router}") String serviceName,
            @Value("${igc.router.build.version:0.0.1-SNAPSHOT}") String serviceVersion) {
        this.serviceName = serviceName;
        this.serviceVersion = serviceVersion;
    }

    @Override
    public ResponseEntity<GetCapabilities200Response> getCapabilities() {
        GetCapabilities200Response body = new GetCapabilities200Response();
        body.setService(serviceName);
        body.setVersion(serviceVersion);
        body.setTiers(List.of("MOCK"));
        body.setModels(List.of());
        return ResponseEntity.ok(body);
    }

    @Override
    public ResponseEntity<HealthResponse> getHealth() {
        HealthResponse body = new HealthResponse();
        body.setStatus(HealthResponse.StatusEnum.UP);
        return ResponseEntity.ok(body);
    }
}
