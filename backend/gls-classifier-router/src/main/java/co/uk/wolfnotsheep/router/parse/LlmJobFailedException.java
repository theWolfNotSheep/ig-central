package co.uk.wolfnotsheep.router.parse;

/**
 * The LLM tier responded but the {@code success} flag is false. The
 * router surfaces this as RFC 7807 502 / {@code ROUTER_LLM_FAILED}
 * (the upstream worker rejected the prompt or hit its own error).
 */
public class LlmJobFailedException extends RuntimeException {
    public LlmJobFailedException(String message) {
        super(message);
    }
}
