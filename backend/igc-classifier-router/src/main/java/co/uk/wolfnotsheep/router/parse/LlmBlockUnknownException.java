package co.uk.wolfnotsheep.router.parse;

/**
 * Thrown when igc-llm-worker returns 422 — the PROMPT block coords
 * don't resolve. Surfaces to the caller as 422
 * {@code ROUTER_LLM_BLOCK_UNKNOWN}.
 */
public class LlmBlockUnknownException extends RuntimeException {

    public LlmBlockUnknownException(String message) {
        super(message);
    }
}
