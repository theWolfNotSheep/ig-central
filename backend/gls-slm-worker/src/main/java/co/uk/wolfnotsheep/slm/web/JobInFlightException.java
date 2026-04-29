package co.uk.wolfnotsheep.slm.web;

public class JobInFlightException extends RuntimeException {
    public JobInFlightException(String nodeRunId) {
        super("an SLM classify call for nodeRunId " + nodeRunId + " is still in flight");
    }
}
