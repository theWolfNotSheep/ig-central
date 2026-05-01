package co.uk.wolfnotsheep.extraction.audio.web;

/**
 * Sync request collided with a sibling that's mid-flight (PENDING /
 * RUNNING). Mapped to RFC 7807 {@code IDEMPOTENCY_IN_FLIGHT} / 409.
 */
public class JobInFlightException extends RuntimeException {
    public JobInFlightException(String nodeRunId) {
        super("an audio transcription for nodeRunId " + nodeRunId + " is still in flight");
    }
}
