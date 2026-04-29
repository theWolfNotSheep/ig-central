package co.uk.wolfnotsheep.router.parse;

/**
 * Thrown when gls-slm-worker returns 422 — the PROMPT block coords
 * don't resolve. Surfaces to the caller as 422
 * {@code ROUTER_SLM_BLOCK_UNKNOWN}; like the BERT equivalent, this
 * is a configuration error that should not silently fall through
 * (the LLM tier can't make up for a broken block).
 */
public class SlmBlockUnknownException extends RuntimeException {

    public SlmBlockUnknownException(String message) {
        super(message);
    }
}
