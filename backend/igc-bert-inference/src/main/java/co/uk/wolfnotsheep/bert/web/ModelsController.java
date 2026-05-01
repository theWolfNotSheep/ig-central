package co.uk.wolfnotsheep.bert.web;

import co.uk.wolfnotsheep.bert.api.ModelsApi;
import co.uk.wolfnotsheep.bert.model.LoadedModelArtifactRef;
import co.uk.wolfnotsheep.bert.model.ModelsResponse;
import co.uk.wolfnotsheep.bert.model.ReloadAccepted;
import co.uk.wolfnotsheep.bert.registry.LoadedModel;
import co.uk.wolfnotsheep.bert.registry.ModelRegistry;
import co.uk.wolfnotsheep.bert.registry.ReloadCoordinator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * Implements {@code GET /v1/models} and
 * {@code POST /v1/models/reload}. Phase 1.4 PR1 wires the contract
 * surface; the actual reload work (MinIO fetch + DJL load) lands in
 * the follow-up PR that swaps the inference engine.
 */
@RestController
public class ModelsController implements ModelsApi {

    private static final Logger log = LoggerFactory.getLogger(ModelsController.class);

    private final ModelRegistry registry;
    private final ReloadCoordinator reloadCoordinator;

    public ModelsController(ModelRegistry registry, ReloadCoordinator reloadCoordinator) {
        this.registry = registry;
        this.reloadCoordinator = reloadCoordinator;
    }

    @Override
    public ResponseEntity<ModelsResponse> listModels() {
        ModelsResponse body = new ModelsResponse();
        List<co.uk.wolfnotsheep.bert.model.LoadedModel> apiModels = new ArrayList<>();
        for (LoadedModel m : registry.snapshot()) {
            co.uk.wolfnotsheep.bert.model.LoadedModel api = new co.uk.wolfnotsheep.bert.model.LoadedModel();
            api.setModelVersion(m.modelVersion());
            if (m.bucket() != null && m.objectKey() != null) {
                LoadedModelArtifactRef ref = new LoadedModelArtifactRef();
                ref.setBucket(m.bucket());
                ref.setObjectKey(m.objectKey());
                api.setArtifactRef(ref);
            }
            api.setLabels(m.labels());
            api.setBlockIds(m.blockIds());
            if (m.loadedAt() != null) {
                api.setLoadedAt(OffsetDateTime.ofInstant(m.loadedAt(), ZoneOffset.UTC));
            }
            apiModels.add(api);
        }
        body.setModels(apiModels);
        return ResponseEntity.ok(body);
    }

    @Override
    public ResponseEntity<ReloadAccepted> reloadModels(String traceparent) {
        Instant startedAt = reloadCoordinator.beginReload();
        try {
            // Phase 1.4 PR1: scaffolds the state-machine without
            // doing the actual fetch. The follow-up PR fires off a
            // background load + populates the registry; this controller
            // returns 202 immediately either way.
            log.info("bert: reload accepted (no-op until DJL engine ships)");
        } finally {
            reloadCoordinator.endReload();
        }
        ReloadAccepted body = new ReloadAccepted();
        body.setStatus(ReloadAccepted.StatusEnum.PENDING);
        body.setStartedAt(OffsetDateTime.ofInstant(startedAt, ZoneOffset.UTC));
        return ResponseEntity.accepted().body(body);
    }
}
