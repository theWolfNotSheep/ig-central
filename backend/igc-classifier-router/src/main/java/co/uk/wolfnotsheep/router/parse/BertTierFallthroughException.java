package co.uk.wolfnotsheep.router.parse;

/**
 * Internal signal that the BERT tier could not produce a usable
 * result and the cascade should escalate to the next tier. Carries
 * the {@code errorCode} for the trace step (e.g.
 * {@code MODEL_NOT_LOADED}, {@code BERT_TRANSPORT_ERROR}) so the
 * fallthrough is observable in the cascade response without
 * surfacing a user-facing error.
 *
 * <p>Not user-facing — caught by {@link BertOrchestratorCascadeService}
 * before it leaves the router.
 */
public class BertTierFallthroughException extends RuntimeException {

    private final String errorCode;

    public BertTierFallthroughException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public BertTierFallthroughException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String errorCode() {
        return errorCode;
    }
}
