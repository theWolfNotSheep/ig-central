package co.uk.wolfnotsheep.slm.web;

import co.uk.wolfnotsheep.slm.api.BackendsApi;
import co.uk.wolfnotsheep.slm.backend.SlmBackendId;
import co.uk.wolfnotsheep.slm.backend.SlmService;
import co.uk.wolfnotsheep.slm.model.BackendInfo;
import co.uk.wolfnotsheep.slm.model.BackendsResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * Implements {@code GET /v1/backends} — reports which backends this
 * build supports + which is currently active. First cut only knows
 * about the not-configured stub; real backends populate this when
 * they ship.
 */
@RestController
public class BackendsController implements BackendsApi {

    private final SlmService backend;

    public BackendsController(SlmService backend) {
        this.backend = backend;
    }

    @Override
    public ResponseEntity<BackendsResponse> listBackends() {
        BackendsResponse body = new BackendsResponse();
        body.setActive(toApiActive(backend.activeBackend()));

        // Phase 1.5 first cut: list both planned backends as
        // unavailable (ready=false). When the real backends ship
        // they'll publish their own readiness via the SlmService
        // interface; this controller can then ask each in turn.
        List<BackendInfo> available = new ArrayList<>();
        available.add(stubInfo(BackendInfo.IdEnum.ANTHROPIC_HAIKU,
                "Cloud SLM via Anthropic. Set igc.slm.worker.backend=anthropic + ANTHROPIC_API_KEY to enable."));
        available.add(stubInfo(BackendInfo.IdEnum.OLLAMA,
                "Local SLM via Ollama. Set igc.slm.worker.backend=ollama + igc.slm.worker.ollama.endpoint to enable."));
        body.setAvailable(available);

        return ResponseEntity.ok(body);
    }

    private static BackendInfo stubInfo(BackendInfo.IdEnum id, String notes) {
        BackendInfo info = new BackendInfo();
        info.setId(id);
        info.setReady(false);
        info.setNotes(notes);
        return info;
    }

    private static BackendsResponse.ActiveEnum toApiActive(SlmBackendId active) {
        return switch (active) {
            case ANTHROPIC_HAIKU -> BackendsResponse.ActiveEnum.ANTHROPIC_HAIKU;
            case OLLAMA -> BackendsResponse.ActiveEnum.OLLAMA;
            case NONE -> BackendsResponse.ActiveEnum.NONE;
        };
    }
}
