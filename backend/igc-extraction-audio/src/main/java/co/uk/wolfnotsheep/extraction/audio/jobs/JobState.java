package co.uk.wolfnotsheep.extraction.audio.jobs;

/**
 * Lifecycle of an audio transcription job. Same enum used in the
 * Mongo-mapped {@link JobRecord} and the contract's
 * {@code JobStatus.status} enum.
 */
public enum JobState {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED
}
