package co.uk.wolfnotsheep.slm.backend;

/**
 * Identifies a configured SLM backend. {@code NONE} is the
 * unconfigured / not-loaded state — every classify call returns
 * {@code SLM_NOT_CONFIGURED} until either {@code ANTHROPIC_HAIKU} or
 * {@code OLLAMA} is wired.
 */
public enum SlmBackendId {
    ANTHROPIC_HAIKU,
    OLLAMA,
    NONE
}
