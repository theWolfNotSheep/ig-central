package co.uk.wolfnotsheep.enforcement.web;

public class JobNotFoundException extends RuntimeException {
    public JobNotFoundException(String nodeRunId) {
        super("no enforcement job exists for nodeRunId " + nodeRunId);
    }
}
