package co.uk.wolfnotsheep.enforcement.web;

public class JobInFlightException extends RuntimeException {
    public JobInFlightException(String nodeRunId) {
        super("an enforcement call for nodeRunId " + nodeRunId + " is still in flight");
    }
}
