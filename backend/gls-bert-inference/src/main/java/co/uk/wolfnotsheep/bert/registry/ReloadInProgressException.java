package co.uk.wolfnotsheep.bert.registry;

/**
 * A reload is already in flight on this replica. Mapped to RFC 7807
 * 409 / {@code MODEL_RELOAD_IN_PROGRESS}. Caller should wait and
 * retry rather than triggering a parallel reload.
 */
public class ReloadInProgressException extends RuntimeException {
    public ReloadInProgressException(String message) {
        super(message);
    }
}
