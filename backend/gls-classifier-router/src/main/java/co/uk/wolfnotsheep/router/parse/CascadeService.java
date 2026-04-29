package co.uk.wolfnotsheep.router.parse;

/**
 * Cascade runner. Phase 1.2 first cut: deterministic mock impl that
 * returns the same shape for every request. BERT / SLM / LLM tiers
 * wire in behind the same interface in 1.4–1.6 without changing
 * callers.
 */
public interface CascadeService {

    /**
     * Run the cascade for {@code blockId} / {@code blockVersion}
     * against {@code text}. Implementations are responsible for any
     * MCP context fetching, tier selection, and threshold evaluation.
     *
     * @param blockId      block document id.
     * @param blockVersion pinned version, or {@code null} for active.
     * @param blockType    declared block type ({@code PROMPT} /
     *                     {@code BERT_CLASSIFIER}), or {@code null}.
     * @param text         the extracted text payload (may be ≤ 256 KB
     *                     inline string or empty if the caller passed
     *                     a textRef the cascade is expected to fetch).
     * @return populated {@link CascadeOutcome}.
     */
    CascadeOutcome run(String blockId, Integer blockVersion, String blockType, String text);
}
