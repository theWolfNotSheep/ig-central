package co.uk.wolfnotsheep.infrastructure.services.pipeline;

import co.uk.wolfnotsheep.document.models.NodeRun;
import co.uk.wolfnotsheep.document.models.NodeRunStatus;
import co.uk.wolfnotsheep.document.models.PipelineRun;
import co.uk.wolfnotsheep.document.models.PipelineRunStatus;
import co.uk.wolfnotsheep.document.models.SystemError;
import co.uk.wolfnotsheep.document.repositories.NodeRunRepository;
import co.uk.wolfnotsheep.document.repositories.PipelineRunRepository;
import co.uk.wolfnotsheep.document.repositories.SystemErrorRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Detect v2 {@link PipelineRun}s stuck in {@link PipelineRunStatus#RUNNING}
 * or {@link PipelineRunStatus#WAITING} for longer than {@link #STALE_THRESHOLD_MINUTES},
 * mark any in-flight {@link NodeRun}s as {@link NodeRunStatus#FAILED} with
 * a {@code STALE_RECOVERY} reason, and either resume the run via
 * {@link PipelineExecutionEngine#resumeRun(String)} (Phase 2.1 PR2) or fail
 * it out when retries are exhausted / the engine isn't on the classpath.
 *
 * <p>Algorithm per stale run:
 * <ol>
 *   <li>Mark every {@link NodeRun} with status {@link NodeRunStatus#RUNNING}
 *       or {@link NodeRunStatus#WAITING} as {@link NodeRunStatus#FAILED} with
 *       {@code error="STALE_RECOVERY: ..."} and {@code completedAt} stamped now.</li>
 *   <li>If {@code retryCount >= MAX_AUTO_RETRIES} → mark the {@link PipelineRun}
 *       {@link PipelineRunStatus#FAILED} (no further auto-retries) and increment
 *       {@code pipeline.stale.exhausted}.</li>
 *   <li>Else if the {@link PipelineExecutionEngine} bean is registered →
 *       increment {@code retryCount}, save the run with {@code status=RUNNING},
 *       call {@link PipelineExecutionEngine#resumeRun(String)} which re-walks
 *       from {@code currentNodeIndex}; increment {@code pipeline.stale.resumed}.</li>
 *   <li>Else (engine absent — legacy v1-only deployment) → fail-out as PR1 did
 *       and increment {@code pipeline.stale.failed}.</li>
 * </ol>
 *
 * <p>Companion to the legacy {@link co.uk.wolfnotsheep.infrastructure.services.StaleDocumentRecoveryTask}
 * which operates on {@code DocumentModel.status} (the v1 form). The two tasks
 * are independent and can run side-by-side — they don't touch each other's
 * state.
 */
@Component
public class StalePipelineRunRecoveryTask {

    private static final Logger log = LoggerFactory.getLogger(StalePipelineRunRecoveryTask.class);

    /** Minutes before a pipeline run is considered stale. */
    static final int STALE_THRESHOLD_MINUTES = 15;

    /** Stop auto-resuming after this many attempts to avoid infinite loops on permanently broken runs. */
    static final int MAX_AUTO_RETRIES = 3;

    private static final Set<PipelineRunStatus> IN_FLIGHT_RUN_STATUSES =
            EnumSet.of(PipelineRunStatus.RUNNING, PipelineRunStatus.WAITING);
    private static final Set<NodeRunStatus> IN_FLIGHT_NODE_STATUSES =
            EnumSet.of(NodeRunStatus.RUNNING, NodeRunStatus.WAITING);

    private final PipelineRunRepository pipelineRunRepo;
    private final NodeRunRepository nodeRunRepo;
    private final SystemErrorRepository systemErrorRepo;
    private final ObjectProvider<MeterRegistry> meterRegistryProvider;
    private final ObjectProvider<PipelineExecutionEngine> engineProvider;

    public StalePipelineRunRecoveryTask(
            PipelineRunRepository pipelineRunRepo,
            NodeRunRepository nodeRunRepo,
            SystemErrorRepository systemErrorRepo,
            ObjectProvider<MeterRegistry> meterRegistryProvider,
            ObjectProvider<PipelineExecutionEngine> engineProvider) {
        this.pipelineRunRepo = pipelineRunRepo;
        this.nodeRunRepo = nodeRunRepo;
        this.systemErrorRepo = systemErrorRepo;
        this.meterRegistryProvider = meterRegistryProvider;
        this.engineProvider = engineProvider;
    }

    @Scheduled(fixedDelay = 300_000, initialDelay = 120_000)
    @SchedulerLock(name = "stale-pipeline-recovery",
            lockAtMostFor = "${igc.pipeline.stale-recovery.lock-at-most-for:PT10M}",
            lockAtLeastFor = "${igc.pipeline.stale-recovery.lock-at-least-for:PT0S}")
    public void recoverStalePipelineRuns() {
        try {
            Instant cutoff = Instant.now().minus(STALE_THRESHOLD_MINUTES, ChronoUnit.MINUTES);
            int detected = 0;
            int resumed = 0;
            int failed = 0;
            int exhausted = 0;
            Instant now = Instant.now();
            for (PipelineRunStatus status : IN_FLIGHT_RUN_STATUSES) {
                List<PipelineRun> stale = pipelineRunRepo.findByStatusAndUpdatedAtBefore(status, cutoff);
                detected += stale.size();
                for (PipelineRun run : stale) {
                    recordDetectionAge(run, now);
                    Outcome outcome = handleStaleRun(run);
                    switch (outcome) {
                        case RESUMED -> resumed++;
                        case FAILED -> failed++;
                        case EXHAUSTED -> exhausted++;
                        case PERSIST_FAILED -> { /* counted as detected only — retry next cycle */ }
                    }
                }
            }
            if (detected > 0) {
                recordCounter("pipeline.stale.detected", detected);
                recordCounter("pipeline.stale.resumed", resumed);
                recordCounter("pipeline.stale.failed", failed);
                recordCounter("pipeline.stale.exhausted", exhausted);
                log.info("Stale pipeline run recovery: {} detected, {} resumed, {} failed-out, {} exhausted",
                        detected, resumed, failed, exhausted);
            }
        } catch (Exception e) {
            log.error("Stale pipeline run recovery task failed: {}", e.getMessage(), e);
            try {
                SystemError err = SystemError.of("ERROR", "PIPELINE",
                        "Stale pipeline run recovery task failed: " + e.getMessage());
                err.setService("api");
                systemErrorRepo.save(err);
            } catch (Exception inner) {
                log.error("Failed to persist recovery task error: {}", inner.getMessage());
            }
        }
    }

    enum Outcome { RESUMED, FAILED, EXHAUSTED, PERSIST_FAILED }

    /**
     * Process a single stale {@link PipelineRun}: fail any in-flight
     * {@link NodeRun}s, then either resume the run (engine present, retries
     * remaining) or fail it out (retries exhausted, or engine not on classpath).
     */
    private Outcome handleStaleRun(PipelineRun run) {
        Instant now = Instant.now();
        try {
            failInFlightNodeRuns(run, now);

            int currentRetryCount = run.getRetryCount();
            PipelineExecutionEngine engine = engineProvider.getIfAvailable();

            if (currentRetryCount >= MAX_AUTO_RETRIES) {
                failRunOut(run, now, "max auto-retries reached (" + MAX_AUTO_RETRIES + ")");
                return Outcome.EXHAUSTED;
            }

            if (engine == null) {
                // Legacy deployment without the v2 engine — no resume entry point available.
                failRunOut(run, now, "engine unavailable for resume");
                return Outcome.FAILED;
            }

            // Resume path: increment retry, set RUNNING, save, hand off to the engine.
            run.setRetryCount(currentRetryCount + 1);
            run.setStatus(PipelineRunStatus.RUNNING);
            run.setUpdatedAt(now);
            pipelineRunRepo.save(run);

            persistSystemError(run, "WARN",
                    "Pipeline run " + run.getId() + " for document " + run.getDocumentId()
                            + " auto-resumed by stale recovery (retry "
                            + run.getRetryCount() + " of " + MAX_AUTO_RETRIES
                            + ", currentNodeKey=" + run.getCurrentNodeKey() + ")");

            try {
                engine.resumeRun(run.getId());
                log.info("Pipeline run {} (document {}) auto-resumed by stale recovery (retry {} of {})",
                        run.getId(), run.getDocumentId(), run.getRetryCount(), MAX_AUTO_RETRIES);
                return Outcome.RESUMED;
            } catch (Exception e) {
                log.error("engine.resumeRun threw for pipeline run {}: {}", run.getId(), e.getMessage(), e);
                // The run is left in RUNNING; the next recovery cycle will see it as
                // stale again and either retry (until MAX_AUTO_RETRIES) or exhaust.
                return Outcome.PERSIST_FAILED;
            }
        } catch (Exception e) {
            log.error("Failed to handle stale pipeline run {}: {}", run.getId(), e.getMessage(), e);
            return Outcome.PERSIST_FAILED;
        }
    }

    private void failInFlightNodeRuns(PipelineRun run, Instant now) {
        List<NodeRun> nodeRuns = nodeRunRepo.findByPipelineRunIdOrderByStartedAtAsc(run.getId());
        for (NodeRun nodeRun : nodeRuns) {
            if (IN_FLIGHT_NODE_STATUSES.contains(nodeRun.getStatus())) {
                nodeRun.setStatus(NodeRunStatus.FAILED);
                nodeRun.setError("STALE_RECOVERY: node in-flight beyond stale threshold ("
                        + STALE_THRESHOLD_MINUTES + " min)");
                nodeRun.setCompletedAt(now);
                if (nodeRun.getStartedAt() != null) {
                    nodeRun.setDurationMs(java.time.Duration.between(nodeRun.getStartedAt(), now).toMillis());
                }
                nodeRunRepo.save(nodeRun);
            }
        }
    }

    private void failRunOut(PipelineRun run, Instant now, String reason) {
        run.setStatus(PipelineRunStatus.FAILED);
        run.setError("STALE_RECOVERY: " + reason
                + " — last currentNodeKey="
                + (run.getCurrentNodeKey() == null ? "<none>" : run.getCurrentNodeKey()));
        if (run.getErrorNodeKey() == null) {
            run.setErrorNodeKey(run.getCurrentNodeKey());
        }
        run.setCompletedAt(now);
        run.setUpdatedAt(now);
        if (run.getStartedAt() != null) {
            run.setTotalDurationMs(java.time.Duration.between(run.getStartedAt(), now).toMillis());
        }
        pipelineRunRepo.save(run);

        persistSystemError(run, "WARN",
                "Pipeline run " + run.getId() + " for document " + run.getDocumentId()
                        + " marked FAILED by stale recovery — " + reason
                        + " (currentNodeKey=" + run.getCurrentNodeKey() + ")");
        log.warn("Pipeline run {} (document {}) marked FAILED by stale recovery — {}",
                run.getId(), run.getDocumentId(), reason);
    }

    private void persistSystemError(PipelineRun run, String severity, String message) {
        try {
            SystemError err = SystemError.of(severity, "PIPELINE", message);
            err.setService("api");
            systemErrorRepo.save(err);
        } catch (Exception e) {
            log.error("Failed to persist SystemError for stale pipeline run {}: {}",
                    run.getId(), e.getMessage());
        }
    }

    private void recordCounter(String name, int delta) {
        if (delta <= 0) return;
        MeterRegistry registry = meterRegistryProvider.getIfAvailable();
        if (registry == null) return;
        Counter.builder(name)
                .description("Stale pipeline run recovery — count of v2 PipelineRuns the recovery task acted on")
                .register(registry)
                .increment(delta);
    }

    /**
     * Record the wall-time gap between the stale run's last activity and
     * the moment the recovery task observed it as stale. Useful for SLO
     * dashboards (p95 detection-lag) — a tight distribution close to
     * {@link #STALE_THRESHOLD_MINUTES} confirms the task is running on
     * schedule; a long tail signals a backlog or a paused replica.
     */
    private void recordDetectionAge(PipelineRun run, Instant now) {
        MeterRegistry registry = meterRegistryProvider.getIfAvailable();
        if (registry == null || run.getUpdatedAt() == null) return;
        long ageSeconds = java.time.Duration.between(run.getUpdatedAt(), now).getSeconds();
        if (ageSeconds < 0) return;
        DistributionSummary.builder("pipeline.stale.detected.age")
                .description("Seconds between a stale PipelineRun's last activity and its detection by the recovery task")
                .baseUnit("seconds")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry)
                .record(ageSeconds);
    }
}
