package co.uk.wolfnotsheep.router.jobs;

/**
 * Lifecycle states of a cascade job. Sync calls touch only
 * {@code PENDING} → {@code RUNNING} → {@code COMPLETED} / {@code FAILED};
 * async calls touch the same states with the producer running in the
 * {@code routerAsyncExecutor} thread pool.
 */
public enum JobState {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED
}
