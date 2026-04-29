package co.uk.wolfnotsheep.router.parse;

/**
 * The LLM tier didn't complete within the configured wait window.
 * The router surfaces this as RFC 7807 504 / {@code ROUTER_LLM_TIMEOUT}.
 */
public class LlmJobTimeoutException extends RuntimeException {
    public LlmJobTimeoutException(String message) {
        super(message);
    }
}
