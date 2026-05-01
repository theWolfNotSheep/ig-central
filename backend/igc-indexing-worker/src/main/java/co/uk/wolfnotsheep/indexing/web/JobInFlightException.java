package co.uk.wolfnotsheep.indexing.web;

public class JobInFlightException extends RuntimeException {
    public JobInFlightException(String nodeRunId) {
        super("an indexing operation for nodeRunId " + nodeRunId + " is still in flight");
    }
}
