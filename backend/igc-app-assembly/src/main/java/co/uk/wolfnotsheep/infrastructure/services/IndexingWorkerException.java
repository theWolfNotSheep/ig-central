package co.uk.wolfnotsheep.infrastructure.services;

/**
 * Transport / parse failure when calling {@code igc-indexing-worker}.
 * The legacy {@link ElasticsearchIndexService} catches this internally
 * and persists a {@code SystemError} — same shape as the existing
 * in-process ES failure path so callers see no behaviour change.
 */
public class IndexingWorkerException extends RuntimeException {
    public IndexingWorkerException(String message) {
        super(message);
    }

    public IndexingWorkerException(String message, Throwable cause) {
        super(message, cause);
    }
}
