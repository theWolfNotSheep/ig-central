package co.uk.wolfnotsheep.bert.inference;

/**
 * Inference strategy. Phase 1.4 PR1 wires only
 * {@link NotLoadedInferenceEngine} (returns {@link ModelNotLoadedException}
 * for every request). The real DJL + ONNX Runtime impl lands when the
 * trainer publishes its first artefact — same interface, no contract
 * change. Implementations are stateless past their loaded model
 * registry; the {@link co.uk.wolfnotsheep.bert.registry.ModelRegistry}
 * tracks which artefacts are loaded.
 */
public interface InferenceEngine {

    /**
     * Run the model bound to {@code blockId} / {@code blockVersion}
     * over {@code text}.
     *
     * @throws ModelNotLoadedException if no model is loaded for the
     *         block — caller maps to 503 so the cascade router falls
     *         through to the next tier.
     * @throws BlockUnknownException if the block id (or pinned
     *         version) doesn't resolve to a registered artefact.
     */
    InferenceResult infer(String blockId, Integer blockVersion, String text);

    /**
     * @return true if at least one model artefact is loaded and the
     *         {@code /actuator/health} readiness can flip UP.
     */
    boolean isReady();
}
