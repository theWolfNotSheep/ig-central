package co.uk.wolfnotsheep.indexing.service;

public class IndexBackendUnavailableException extends RuntimeException {
    public IndexBackendUnavailableException(String message) {
        super(message);
    }

    public IndexBackendUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
