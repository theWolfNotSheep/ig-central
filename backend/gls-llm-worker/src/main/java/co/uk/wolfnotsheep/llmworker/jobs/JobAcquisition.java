package co.uk.wolfnotsheep.llmworker.jobs;

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
