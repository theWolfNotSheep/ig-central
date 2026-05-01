package co.uk.wolfnotsheep.slm.backend;

import java.util.Map;

/**
 * Successful SLM classification shape, returned by
 * {@link SlmService#classify}.
 *
 * @param result      Block-shaped output map (e.g. {@code {category, sensitivity, confidence}}).
 * @param confidence  Backend-reported confidence in {@code [0, 1]}.
 * @param rationale   Optional free-text rationale.
 * @param backend     Which backend produced the result.
 * @param modelId     Backend-specific model identifier (audit pin).
 * @param tokensIn    Tokens consumed in the request, or 0 if not reported.
 * @param tokensOut   Tokens produced in the response, or 0 if not reported.
 */
public record SlmResult(
        Map<String, Object> result,
        float confidence,
        String rationale,
        SlmBackendId backend,
        String modelId,
        long tokensIn,
        long tokensOut
) {
}
