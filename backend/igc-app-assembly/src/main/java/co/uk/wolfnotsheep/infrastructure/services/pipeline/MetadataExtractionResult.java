package co.uk.wolfnotsheep.infrastructure.services.pipeline;

import java.util.Map;

/**
 * Result of dispatching a single {@code metadataSchemaIds[]} entry from
 * a resolved POLICY block through {@code igc-classifier-router} (Phase
 * 1.9 PR3 / CSV #36).
 *
 * <p>The dispatcher records one of these per schema in shared context
 * key {@code policyExtractionResults}; PR4 (results aggregated, passed
 * to enforcement) consumes them and persists to the document.
 *
 * @param schemaId        Echo of the metadata schema id the dispatcher invoked.
 * @param blockRef        PROMPT block id used (typically
 *                        {@code extract-metadata-${schemaId}}).
 * @param dispatched      {@code true} if the router was reachable + the
 *                        request was sent. {@code false} when the
 *                        client bean isn't wired (test contexts) — the
 *                        dispatcher records the row but skips the HTTP
 *                        call.
 * @param tierOfDecision  Cascade tier that produced the result
 *                        (BERT / SLM / LLM / MOCK). Null on dispatch
 *                        failure.
 * @param confidence      Tier-reported confidence; null on failure.
 * @param extractedFields Field-keyed extracted values. The seeded
 *                        systemPrompt instructs the model to return
 *                        strict JSON keyed by {@code fieldName} with
 *                        type-appropriate values; the dispatcher
 *                        passes that through verbatim.
 * @param error           Human-readable error message; null on success.
 * @param durationMs      Wall-clock duration; {@code -1} when not dispatched.
 */
public record MetadataExtractionResult(
        String schemaId,
        String blockRef,
        boolean dispatched,
        String tierOfDecision,
        Double confidence,
        Map<String, Object> extractedFields,
        String error,
        long durationMs
) {

    public MetadataExtractionResult {
        extractedFields = extractedFields == null ? Map.of() : Map.copyOf(extractedFields);
    }

    public boolean success() {
        return dispatched && error == null;
    }
}
