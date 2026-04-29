package co.uk.wolfnotsheep.router.parse;

/**
 * Parsed cascade policy from a ROUTER block (per
 * {@code contracts/blocks/router.schema.json}). Per-tier
 * {@code enabled} + {@code accept} threshold.
 *
 * <p>The orchestrators use this to gate tier outcomes: when a tier's
 * confidence is below {@code accept} (or {@code enabled=false}), the
 * orchestrator treats the result as a fallthrough and escalates to
 * the next tier — same shape as a transport / 503 fallthrough.
 *
 * <p>{@link #DEFAULT} matches the seeded {@code default-router} block
 * (per Mongock {@code V003_DefaultRouterBlock}): all tiers enabled,
 * BERT/SLM accept = 1.01 (functionally disabled), LLM accept = 0.0
 * (always accept). With this default the cascade always escalates to
 * LLM until per-category tuning lands.
 */
public record RouterPolicy(
        TierPolicy bert,
        TierPolicy slm,
        TierPolicy llm) {

    public static final RouterPolicy DEFAULT = new RouterPolicy(
            new TierPolicy(true, 1.01f),
            new TierPolicy(true, 1.01f),
            new TierPolicy(true, 0.0f));

    public record TierPolicy(boolean enabled, float accept) {
    }
}
