package co.uk.wolfnotsheep.router.jobs;

/**
 * Outcome of a {@link JobStore#tryAcquire(String)} call:
 *
 * <ul>
 *     <li>{@code ACQUIRED} — caller is the producer; proceeds to
 *         {@code markRunning}.</li>
 *     <li>{@code RUNNING} — a sibling owns the row;
 *         {@link #existing()} is the in-flight {@link JobRecord}.
 *         Sync callers raise 409; async callers return the poll URL.</li>
 *     <li>{@code COMPLETED} — cached terminal row;
 *         {@link #existing()} carries the cached {@code resultJson}.</li>
 *     <li>{@code FAILED} — cached failure row;
 *         {@link #existing()} carries {@code errorCode} / {@code errorMessage}.</li>
 * </ul>
 */
public record JobAcquisition(Status status, JobRecord existing) {

    public enum Status { ACQUIRED, RUNNING, COMPLETED, FAILED }

    public static JobAcquisition acquired() {
        return new JobAcquisition(Status.ACQUIRED, null);
    }

    public static JobAcquisition running(JobRecord existing) {
        return new JobAcquisition(Status.RUNNING, existing);
    }

    public static JobAcquisition completed(JobRecord existing) {
        return new JobAcquisition(Status.COMPLETED, existing);
    }

    public static JobAcquisition failed(JobRecord existing) {
        return new JobAcquisition(Status.FAILED, existing);
    }
}
