package co.uk.wolfnotsheep.llmworker.backend;

/**
 * Identifies a configured LLM backend. {@code NONE} is the
 * unconfigured / not-loaded state — every classify call returns
 * {@code LLM_NOT_CONFIGURED} until either {@code ANTHROPIC} or
 * {@code OLLAMA} is wired (Phase 1.6 PR2).
 */
public enum LlmBackendId {
    ANTHROPIC,
    OLLAMA,
    NONE
}
