package co.uk.wolfnotsheep.router.parse;

/**
 * Internal signal that the SLM tier could not produce a usable
 * result and the cascade should escalate to the next tier (LLM).
 * Carries the {@code errorCode} for the trace step (e.g.
 * {@code SLM_NOT_CONFIGURED}, {@code SLM_TRANSPORT_ERROR}).
 *
 * <p>Not user-facing — caught by {@link SlmOrchestratorCascadeService}
 * before it leaves the router.
 */
public class SlmTierFallthroughException extends RuntimeException {

    private final String errorCode;

    public SlmTierFallthroughException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public SlmTierFallthroughException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String errorCode() {
        return errorCode;
    }
}
