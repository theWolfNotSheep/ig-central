package co.uk.wolfnotsheep.router.web;

/**
 * Polled {@code GET /v1/jobs/{nodeRunId}} for an id that has no row
 * in {@code router_jobs}. Mapped to RFC 7807 404
 * {@code ROUTER_JOB_NOT_FOUND}.
 */
public class JobNotFoundException extends RuntimeException {
    public JobNotFoundException(String nodeRunId) {
        super("no router job exists for nodeRunId " + nodeRunId);
    }
}
