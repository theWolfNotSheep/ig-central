package co.uk.wolfnotsheep.infrastructure.services.pipeline;

import java.util.Map;

/**
 * Result of dispatching a single {@code requiredScans[]} entry from a
 * resolved POLICY block through {@code gls-classifier-router} (Phase
 * 1.9 / CSV #36).
 *
 * <p>The dispatcher records one of these per scan in shared context
 * key {@code policyScanResults}; PR3 (metadata extraction) and PR4
 * (enforcement handoff) consume them.
 *
 * @param scanType        Echo of the scan family (PII / PHI / PCI / CUSTOM).
 * @param ref             Block id / scan ref the dispatcher invoked.
 * @param blocking        Whether the scan was declared blocking on the POLICY block.
 * @param dispatched      {@code true} if the router was reachable + the
 *                        request was sent. {@code false} when the
 *                        client bean isn't wired (e.g. test contexts) —
 *                        the dispatcher records the row but skips the
 *                        HTTP call.
 * @param tierOfDecision  Cascade tier that produced the result
 *                        (BERT / SLM / LLM / MOCK). Null on dispatch
 *                        failure.
 * @param confidence      Tier-reported confidence; null on failure.
 * @param result          Raw block-shaped output as parsed from the
 *                        router response. For SCAN PROMPTs this is the
 *                        {@code {found, instances, confidence}} the
 *                        seeded systemPrompt instructs the model to
 *                        return. Empty map (not null) when the result
 *                        couldn't be parsed.
 * @param error           Human-readable error message; null on success.
 *                        Populated for both transport failures and
 *                        non-2xx router responses.
 * @param durationMs      Wall-clock duration of the HTTP call;
 *                        {@code -1} when not dispatched.
 */
public record PolicyScanResult(
        String scanType,
        String ref,
        boolean blocking,
        boolean dispatched,
        String tierOfDecision,
        Double confidence,
        Map<String, Object> result,
        String error,
        long durationMs
) {

    public PolicyScanResult {
        result = result == null ? Map.of() : Map.copyOf(result);
    }

    public boolean success() {
        return dispatched && error == null;
    }

    public boolean blockingFailure() {
        return blocking && !success();
    }
}
