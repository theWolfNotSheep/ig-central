package co.uk.wolfnotsheep.infrastructure.services.pipeline;

/**
 * Transport / parse failure when calling
 * {@code igc-enforcement-worker}. The engine treats this as an
 * enforcement-stage failure for the document — same shape as the
 * legacy in-process {@code EnforcementService} throwing.
 */
public class EnforcementWorkerException extends RuntimeException {
    public EnforcementWorkerException(String message) {
        super(message);
    }

    public EnforcementWorkerException(String message, Throwable cause) {
        super(message, cause);
    }
}
