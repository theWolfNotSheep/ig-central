package co.uk.wolfnotsheep.slm.web;

import co.uk.wolfnotsheep.slm.api.MetaApi;
import co.uk.wolfnotsheep.slm.backend.SlmBackendId;
import co.uk.wolfnotsheep.slm.backend.SlmService;
import co.uk.wolfnotsheep.slm.model.GetCapabilities200Response;
import co.uk.wolfnotsheep.slm.model.GetCapabilities200ResponseModelsInner;
import co.uk.wolfnotsheep.slm.model.HealthResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
public class MetaController implements MetaApi {

    private final String serviceName;
    private final String serviceVersion;
    private final SlmService backend;

    public MetaController(
            @Value("${spring.application.name:igc-slm-worker}") String serviceName,
            @Value("${igc.slm.worker.build.version:0.0.1-SNAPSHOT}") String serviceVersion,
            SlmService backend) {
        this.serviceName = serviceName;
        this.serviceVersion = serviceVersion;
        this.backend = backend;
    }

    @Override
    public ResponseEntity<GetCapabilities200Response> getCapabilities() {
        GetCapabilities200Response body = new GetCapabilities200Response();
        body.setService(serviceName);
        body.setVersion(serviceVersion);
        body.setTiers(List.of("SLM"));
        List<GetCapabilities200ResponseModelsInner> models = new ArrayList<>();
        if (backend.activeBackend() != SlmBackendId.NONE) {
            GetCapabilities200ResponseModelsInner inner = new GetCapabilities200ResponseModelsInner();
            inner.setName(backend.activeBackend().name());
            models.add(inner);
        }
        body.setModels(models);
        return ResponseEntity.ok(body);
    }

    @Override
    public ResponseEntity<HealthResponse> getHealth() {
        boolean ready = backend.isReady();
        HealthResponse body = new HealthResponse();
        body.setStatus(ready ? HealthResponse.StatusEnum.UP : HealthResponse.StatusEnum.OUT_OF_SERVICE);
        return ResponseEntity.status(ready ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }
}
