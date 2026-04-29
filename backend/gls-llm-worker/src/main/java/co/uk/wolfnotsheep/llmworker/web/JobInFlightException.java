package co.uk.wolfnotsheep.llmworker.web;

public class JobInFlightException extends RuntimeException {
    public JobInFlightException(String nodeRunId) {
        super("an LLM classify call for nodeRunId " + nodeRunId + " is still in flight");
    }
}
