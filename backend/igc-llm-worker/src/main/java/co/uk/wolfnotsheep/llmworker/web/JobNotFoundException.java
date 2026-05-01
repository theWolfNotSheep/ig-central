package co.uk.wolfnotsheep.llmworker.web;

public class JobNotFoundException extends RuntimeException {
    public JobNotFoundException(String nodeRunId) {
        super("no LLM job exists for nodeRunId " + nodeRunId);
    }
}
