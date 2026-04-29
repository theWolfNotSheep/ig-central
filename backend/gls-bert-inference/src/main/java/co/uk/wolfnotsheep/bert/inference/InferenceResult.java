package co.uk.wolfnotsheep.bert.inference;

import java.util.List;

/**
 * Result of one BERT inference call.
 *
 * @param label         predicted label.
 * @param confidence    softmax confidence on {@code label}.
 * @param scores        full distribution (label + confidence per
 *                      class). Empty list when the engine doesn't
 *                      surface it.
 * @param modelVersion  semver of the artefact that produced the
 *                      result; surfaced on the response and on the
 *                      audit envelope.
 * @param byteCount     UTF-8 byte length of the input text. Drives
 *                      {@code costUnits} per CSV #22.
 */
public record InferenceResult(
        String label,
        float confidence,
        List<LabelScore> scores,
        String modelVersion,
        long byteCount
) {

    public record LabelScore(String label, float confidence) {
    }
}
