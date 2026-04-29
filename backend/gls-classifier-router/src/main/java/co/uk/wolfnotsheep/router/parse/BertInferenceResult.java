package co.uk.wolfnotsheep.router.parse;

/**
 * Successful BERT-tier inference shape, decoded from
 * {@code gls-bert-inference}'s {@code POST /v1/infer} 200 response.
 *
 * @param label        Predicted label from the model's training labels.
 * @param confidence   Softmax confidence in {@code [0, 1]}.
 * @param modelVersion Semantic version of the ONNX artefact —
 *                     pinned in the cascade trace so audit can map
 *                     the inference back to a specific artefact.
 */
public record BertInferenceResult(
        String label,
        float confidence,
        String modelVersion
) {
}
