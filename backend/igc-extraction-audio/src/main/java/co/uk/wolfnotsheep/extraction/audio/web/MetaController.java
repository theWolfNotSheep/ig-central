package co.uk.wolfnotsheep.extraction.audio.web;

import co.uk.wolfnotsheep.extraction.audio.api.MetaApi;
import co.uk.wolfnotsheep.extraction.audio.model.GetCapabilities200Response;
import co.uk.wolfnotsheep.extraction.audio.model.HealthResponse;
import co.uk.wolfnotsheep.extraction.audio.parse.AudioTranscriptionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class MetaController implements MetaApi {

    private final String serviceName;
    private final String serviceVersion;
    private final AudioTranscriptionService backend;

    public MetaController(
            @Value("${spring.application.name:igc-extraction-audio}") String serviceName,
            @Value("${igc.extraction.audio.build.version:0.0.1-SNAPSHOT}") String serviceVersion,
            AudioTranscriptionService backend) {
        this.serviceName = serviceName;
        this.serviceVersion = serviceVersion;
        this.backend = backend;
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
        HealthResponse body = new HealthResponse();
        body.setStatus(backend.isReady()
                ? HealthResponse.StatusEnum.UP
                : HealthResponse.StatusEnum.OUT_OF_SERVICE);
        return ResponseEntity.status(backend.isReady() ? 200 : 503).body(body);
    }
}
