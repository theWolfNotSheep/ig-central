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
import io.micrometer.core.instrument.MeterRegistry;
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
 * Phase 2.1 PR1 — detect v2 {@link PipelineRun}s stuck in {@link PipelineRunStatus#RUNNING}
 * or {@link PipelineRunStatus#WAITING} for longer than {@link #STALE_THRESHOLD_MINUTES}
 * and fail them out so they no longer sit invisibly stuck.
 *
 * <p>Behaviour for every stale run:
 * <ul>
 *   <li>any {@link NodeRun} with status {@link NodeRunStatus#RUNNING} or
 *       {@link NodeRunStatus#WAITING} is marked {@link NodeRunStatus#FAILED} with
 *       a {@code STALE_RECOVERY} reason and {@code completedAt} stamped now;</li>
 *   <li>the {@link PipelineRun} itself is marked {@link PipelineRunStatus#FAILED}
 *       with {@code error="STALE_RECOVERY: ..."}, {@code errorNodeKey} preserved
 *       from {@code currentNodeKey}, and {@code completedAt} stamped now;</li>
 *   <li>a {@link SystemError} row is persisted so operators see it on the
 *       monitoring page;</li>
 *   <li>Micrometer counters {@code pipeline.stale.detected} and
 *       {@code pipeline.stale.failed} are incremented.</li>
 * </ul>
 *
 * <p>Auto-resume from the last completed node is deferred to Phase 2.1 PR2 —
 * needs a public {@code engine.resumeRun(pipelineRunId)} entry point that
 * doesn't exist yet. Until that lands, operators retry stale runs manually
 * via the existing reprocess UI.
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

    private static final Set<PipelineRunStatus> IN_FLIGHT_RUN_STATUSES =
            EnumSet.of(PipelineRunStatus.RUNNING, PipelineRunStatus.WAITING);
    private static final Set<NodeRunStatus> IN_FLIGHT_NODE_STATUSES =
            EnumSet.of(NodeRunStatus.RUNNING, NodeRunStatus.WAITING);

    private final PipelineRunRepository pipelineRunRepo;
    private final NodeRunRepository nodeRunRepo;
    private final SystemErrorRepository systemErrorRepo;
    private final ObjectProvider<MeterRegistry> meterRegistryProvider;

    public StalePipelineRunRecoveryTask(
            PipelineRunRepository pipelineRunRepo,
            NodeRunRepository nodeRunRepo,
            SystemErrorRepository systemErrorRepo,
            ObjectProvider<MeterRegistry> meterRegistryProvider) {
        this.pipelineRunRepo = pipelineRunRepo;
        this.nodeRunRepo = nodeRunRepo;
        this.systemErrorRepo = systemErrorRepo;
        this.meterRegistryProvider = meterRegistryProvider;
    }

    @Scheduled(fixedDelay = 300_000, initialDelay = 120_000)
    public void recoverStalePipelineRuns() {
        try {
            Instant cutoff = Instant.now().minus(STALE_THRESHOLD_MINUTES, ChronoUnit.MINUTES);
            int detected = 0;
            int failed = 0;
            for (PipelineRunStatus status : IN_FLIGHT_RUN_STATUSES) {
                List<PipelineRun> stale = pipelineRunRepo.findByStatusAndUpdatedAtBefore(status, cutoff);
                detected += stale.size();
                for (PipelineRun run : stale) {
                    if (failPipelineRun(run)) {
                        failed++;
                    }
                }
            }
            if (detected > 0) {
                recordCounter("pipeline.stale.detected", detected);
                recordCounter("pipeline.stale.failed", failed);
                log.info("Stale pipeline run recovery: {} detected, {} marked FAILED", detected, failed);
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

    /**
     * Mark a single stale {@link PipelineRun} and any in-flight {@link NodeRun}s
     * underneath it as {@link PipelineRunStatus#FAILED} / {@link NodeRunStatus#FAILED}.
     *
     * @return {@code true} if the run was successfully failed-out; {@code false}
     *         if persistence raised — the next iteration will retry.
     */
    private boolean failPipelineRun(PipelineRun run) {
        Instant now = Instant.now();
        try {
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

            run.setStatus(PipelineRunStatus.FAILED);
            run.setError("STALE_RECOVERY: pipeline run in-flight beyond stale threshold ("
                    + STALE_THRESHOLD_MINUTES + " min) — last currentNodeKey="
                    + (run.getCurrentNodeKey() == null ? "<none>" : run.getCurrentNodeKey()));
            // errorNodeKey already left at currentNodeKey (set by the engine when the run last advanced)
            if (run.getErrorNodeKey() == null) {
                run.setErrorNodeKey(run.getCurrentNodeKey());
            }
            run.setCompletedAt(now);
            run.setUpdatedAt(now);
            if (run.getStartedAt() != null) {
                run.setTotalDurationMs(java.time.Duration.between(run.getStartedAt(), now).toMillis());
            }
            pipelineRunRepo.save(run);

            persistSystemError(run);
            log.warn("Pipeline run {} (document {}) marked FAILED by stale recovery — currentNodeKey={}",
                    run.getId(), run.getDocumentId(), run.getCurrentNodeKey());
            return true;
        } catch (Exception e) {
            log.error("Failed to fail-out stale pipeline run {}: {}", run.getId(), e.getMessage(), e);
            return false;
        }
    }

    private void persistSystemError(PipelineRun run) {
        try {
            SystemError err = SystemError.of("WARN", "PIPELINE",
                    "Pipeline run " + run.getId() + " for document " + run.getDocumentId()
                            + " marked FAILED by stale recovery (currentNodeKey="
                            + run.getCurrentNodeKey() + ")");
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
}
