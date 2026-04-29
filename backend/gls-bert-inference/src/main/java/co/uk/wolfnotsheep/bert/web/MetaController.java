package co.uk.wolfnotsheep.bert.web;

import co.uk.wolfnotsheep.bert.api.MetaApi;
import co.uk.wolfnotsheep.bert.inference.InferenceEngine;
import co.uk.wolfnotsheep.bert.model.GetCapabilities200Response;
import co.uk.wolfnotsheep.bert.model.HealthResponse;
import co.uk.wolfnotsheep.bert.registry.ModelRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class MetaController implements MetaApi {

    private final String serviceName;
    private final String serviceVersion;
    private final InferenceEngine engine;
    private final ModelRegistry registry;

    public MetaController(
            @Value("${spring.application.name:gls-bert-inference}") String serviceName,
            @Value("${gls.bert.build.version:0.0.1-SNAPSHOT}") String serviceVersion,
            InferenceEngine engine,
            ModelRegistry registry) {
        this.serviceName = serviceName;
        this.serviceVersion = serviceVersion;
        this.engine = engine;
        this.registry = registry;
    }

    @Override
    public ResponseEntity<GetCapabilities200Response> getCapabilities() {
        GetCapabilities200Response body = new GetCapabilities200Response();
        body.setService(serviceName);
        body.setVersion(serviceVersion);
        body.setTiers(List.of("BERT"));
        // models[] reports loaded artefacts so the cascade router can
        // see at glance which BERT_CLASSIFIER blocks are servable on
        // this replica without a separate /v1/models call.
        List<co.uk.wolfnotsheep.bert.model.GetCapabilities200ResponseModelsInner> apiModels = new java.util.ArrayList<>();
        registry.snapshot().forEach(m -> {
            var inner = new co.uk.wolfnotsheep.bert.model.GetCapabilities200ResponseModelsInner();
            inner.setName("BERT");
            inner.setVersion(m.modelVersion());
            apiModels.add(inner);
        });
        body.setModels(apiModels);
        return ResponseEntity.ok(body);
    }

    @Override
    public ResponseEntity<HealthResponse> getHealth() {
        boolean ready = engine.isReady() && !registry.isEmpty();
        HealthResponse body = new HealthResponse();
        body.setStatus(ready ? HealthResponse.StatusEnum.UP : HealthResponse.StatusEnum.OUT_OF_SERVICE);
        return ResponseEntity.status(ready ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }
}
