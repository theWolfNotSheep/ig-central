package co.uk.wolfnotsheep.extraction.audio.web;

public class JobNotFoundException extends RuntimeException {
    public JobNotFoundException(String nodeRunId) {
        super("no audio transcription job for nodeRunId " + nodeRunId);
    }
}
