package co.uk.wolfnotsheep.router.parse;

import java.util.List;
import java.util.Map;

/**
 * Output of one cascade run. Phase 1.2 first cut returns a
 * deterministic mock; the shape is stable so wiring real BERT / SLM
 * / LLM tiers in 1.4–1.6 doesn't reshape callers.
 *
 * @param tierOfDecision   Tier that produced the result (or {@code MOCK}
 *                         in the first cut).
 * @param confidence       Confidence the chosen tier reported.
 * @param result           Block-shaped output map.
 * @param rationale        Optional free-text rationale (LLM tier only).
 * @param evidence         Optional evidence pointers passed through
 *                         from the block.
 * @param trace            Per-tier breakdown of the cascade's behaviour.
 * @param costUnits        Sum of per-tier costs.
 * @param byteCount        Source bytes consumed (drives the request's
 *                         {@code costUnits} per CSV #22).
 */
public record CascadeOutcome(
        String tierOfDecision,
        float confidence,
        Map<String, Object> result,
        String rationale,
        List<String> evidence,
        List<TraceStep> trace,
        long costUnits,
        long byteCount
) {

    public record TraceStep(
            String tier,
            boolean accepted,
            Float confidence,
            Long durationMs,
            Long costUnits,
            String errorCode
    ) {
    }
}
