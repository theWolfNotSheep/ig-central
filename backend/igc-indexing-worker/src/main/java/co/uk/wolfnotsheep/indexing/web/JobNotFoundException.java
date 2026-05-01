package co.uk.wolfnotsheep.indexing.web;

public class JobNotFoundException extends RuntimeException {
    public JobNotFoundException(String nodeRunId) {
        super("no indexing job exists for nodeRunId " + nodeRunId);
    }
}
