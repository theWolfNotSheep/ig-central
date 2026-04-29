package co.uk.wolfnotsheep.bert.inference;

/**
 * No model is loaded on this replica yet. Mapped to RFC 7807 503 /
 * {@code MODEL_NOT_LOADED}; the cascade router treats this as a
 * tier-skip and falls through.
 */
public class ModelNotLoadedException extends RuntimeException {
    public ModelNotLoadedException(String message) {
        super(message);
    }
}
