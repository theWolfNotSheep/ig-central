package co.uk.wolfnotsheep.extraction.audio.jobs;

/**
 * Outcome of {@link JobStore#tryAcquire}. ACQUIRED = first time;
 * caller proceeds. RUNNING = a sibling is mid-flight (sync path
 * returns 409, async path returns the existing job's poll URL via
 * a {@code JobAccepted} response). COMPLETED = cached result; sync
 * path returns it, async path returns the COMPLETED job.
 */
public record JobAcquisition(Status status, JobRecord existing) {

    public enum Status { ACQUIRED, RUNNING, COMPLETED, FAILED }

    public static JobAcquisition acquired() {
        return new JobAcquisition(Status.ACQUIRED, null);
    }

    public static JobAcquisition running(JobRecord row) {
        return new JobAcquisition(Status.RUNNING, row);
    }

    public static JobAcquisition completed(JobRecord row) {
        return new JobAcquisition(Status.COMPLETED, row);
    }

    public static JobAcquisition failed(JobRecord row) {
        return new JobAcquisition(Status.FAILED, row);
    }
}
