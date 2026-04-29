package co.uk.wolfnotsheep.infrastructure.services.pipeline;

/**
 * Transport / parse failure when calling
 * {@code gls-classifier-router}. The engine treats this as a
 * classification failure for the document — same shape as a Rabbit
 * publish-side failure on the existing async path.
 */
public class ClassifierRouterException extends RuntimeException {
    public ClassifierRouterException(String message) {
        super(message);
    }

    public ClassifierRouterException(String message, Throwable cause) {
        super(message, cause);
    }
}
