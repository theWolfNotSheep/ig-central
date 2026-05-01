package co.uk.wolfnotsheep.slm.backend;

/**
 * Thrown when the PROMPT block coords don't resolve. Mapped to
 * 422 {@code SLM_BLOCK_UNKNOWN}; this is a configuration error that
 * should not silently fall through.
 */
public class BlockUnknownException extends RuntimeException {
    public BlockUnknownException(String message) {
        super(message);
    }
}
