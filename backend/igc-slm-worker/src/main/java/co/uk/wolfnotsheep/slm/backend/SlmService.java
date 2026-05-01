package co.uk.wolfnotsheep.slm.backend;

/**
 * SLM backend abstraction. Phase 1.5 first cut ships a
 * {@link NotConfiguredSlmService}; real Anthropic Haiku and Ollama
 * backends slot in behind this interface in follow-up PRs.
 */
public interface SlmService {

    /**
     * Run the configured backend over {@code text} guided by the
     * PROMPT block referenced by {@code blockId} / {@code blockVersion}.
     *
     * @param blockId      PROMPT block document id.
     * @param blockVersion pinned block version, or {@code null} for
     *                     active.
     * @param text         the extracted text payload (may be ≤ 256 KB
     *                     inline string or empty for textRef branches
     *                     not yet implemented in the first cut).
     * @return populated {@link SlmResult}.
     * @throws SlmNotConfiguredException if no backend is wired.
     * @throws BlockUnknownException if the block coords don't resolve.
     */
    SlmResult classify(String blockId, Integer blockVersion, String text);

    /** Currently active backend, or {@link SlmBackendId#NONE}. */
    SlmBackendId activeBackend();

    /**
     * Service is "ready" only when an actual backend is configured
     * + reachable. The not-loaded stub returns {@code false} so
     * Kubernetes / Compose probes treat the replica as not ready.
     */
    boolean isReady();
}
