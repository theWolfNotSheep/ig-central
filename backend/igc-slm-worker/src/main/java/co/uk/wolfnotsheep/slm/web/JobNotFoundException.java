package co.uk.wolfnotsheep.slm.web;

public class JobNotFoundException extends RuntimeException {
    public JobNotFoundException(String nodeRunId) {
        super("no SLM job exists for nodeRunId " + nodeRunId);
    }
}
