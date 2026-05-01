package co.uk.wolfnotsheep.llmworker.backend;

import java.util.Map;

/**
 * Successful LLM classification shape, returned by
 * {@link LlmService#classify}.
 *
 * @param result      Block-shaped output map (e.g. {@code {category, sensitivity, confidence}}).
 * @param confidence  Backend-reported confidence in {@code [0, 1]}.
 * @param rationale   Optional free-text rationale.
 * @param backend     Which backend produced the result.
 * @param modelId     Backend-specific model identifier (audit pin).
 * @param tokensIn    Tokens consumed in the request, or 0 if not reported.
 * @param tokensOut   Tokens produced in the response, or 0 if not reported.
 */
public record LlmResult(
        Map<String, Object> result,
        float confidence,
        String rationale,
        LlmBackendId backend,
        String modelId,
        long tokensIn,
        long tokensOut
) {
}
