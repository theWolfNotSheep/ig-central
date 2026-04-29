package co.uk.wolfnotsheep.bert.registry;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory registry of loaded ONNX artefacts. Phase 1.4 PR1 ships
 * empty; the real DJL load path populates it on startup + reload.
 *
 * <p>{@link CopyOnWriteArrayList} keeps {@link #snapshot()} cheap +
 * lock-free for the read-heavy path (every {@code GET /v1/models}
 * call). Updates from the load path are rare.
 */
@Component
public class ModelRegistry {

    private final List<LoadedModel> models = new CopyOnWriteArrayList<>();

    public void replace(List<LoadedModel> next) {
        models.clear();
        models.addAll(next);
    }

    public List<LoadedModel> snapshot() {
        return List.copyOf(models);
    }

    public boolean isEmpty() {
        return models.isEmpty();
    }
}
