package co.uk.wolfnotsheep.router.parse;

import java.util.Map;

/**
 * Successful SLM-tier result, decoded from
 * {@code gls-slm-worker}'s {@code POST /v1/classify} 200 response.
 *
 * @param result      Block-shaped output map (e.g. {@code {category, sensitivity}}).
 * @param confidence  Confidence in {@code [0, 1]}.
 * @param backend     Backend that produced the result (audit pin).
 * @param modelId     Backend-specific model identifier (audit pin).
 * @param tokensIn    Tokens consumed; may be 0 if not reported.
 * @param tokensOut   Tokens produced; may be 0 if not reported.
 * @param costUnits   Per-call cost the worker reported (CSV #22).
 */
public record SlmInferenceResult(
        Map<String, Object> result,
        float confidence,
        String backend,
        String modelId,
        long tokensIn,
        long tokensOut,
        long costUnits
) {
}
