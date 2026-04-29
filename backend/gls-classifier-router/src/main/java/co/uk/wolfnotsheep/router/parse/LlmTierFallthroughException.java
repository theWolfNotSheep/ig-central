package co.uk.wolfnotsheep.router.parse;

/**
 * Internal signal that the LLM HTTP tier could not produce a usable
 * result. The LLM tier is the cascade's floor, so a fallthrough here
 * is unusual — it either means the worker is misconfigured (no
 * backend wired) or there's a transport problem. The router maps
 * this to {@code ROUTER_LLM_FAILED} 502 (bad gateway), same as the
 * legacy Rabbit-based failure path.
 *
 * <p>Carries the {@code errorCode} for the trace step.
 */
public class LlmTierFallthroughException extends RuntimeException {

    private final String errorCode;

    public LlmTierFallthroughException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public LlmTierFallthroughException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String errorCode() {
        return errorCode;
    }
}
