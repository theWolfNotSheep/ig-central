package co.uk.wolfnotsheep.bert.inference;

/**
 * Default engine for builds that haven't loaded a model yet. Always
 * throws {@link ModelNotLoadedException}; the controller maps to
 * 503 so the cascade router falls through to the next tier.
 *
 * <p>Swapped out by the real DJL + ONNX Runtime engine when the
 * trainer publishes an artefact and {@code igc.bert.inference.engine}
 * is set to {@code djl}.
 */
public class NotLoadedInferenceEngine implements InferenceEngine {

    @Override
    public InferenceResult infer(String blockId, Integer blockVersion, String text) {
        throw new ModelNotLoadedException(
                "no BERT model is loaded on this replica. Set igc.bert.inference.engine=djl "
                        + "and configure igc.bert.inference.minio.bucket / object-key once the trainer "
                        + "publishes an ONNX artefact.");
    }

    @Override
    public boolean isReady() {
        return false;
    }
}
