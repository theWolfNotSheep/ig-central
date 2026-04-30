package co.uk.wolfnotsheep.infrastructure.services.pipeline;

import co.uk.wolfnotsheep.document.models.NodeRun;
import co.uk.wolfnotsheep.document.models.NodeRunStatus;
import co.uk.wolfnotsheep.document.models.PipelineRun;
import co.uk.wolfnotsheep.document.models.PipelineRunStatus;
import co.uk.wolfnotsheep.document.models.SystemError;
import co.uk.wolfnotsheep.document.repositories.NodeRunRepository;
import co.uk.wolfnotsheep.document.repositories.PipelineRunRepository;
import co.uk.wolfnotsheep.document.repositories.SystemErrorRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StalePipelineRunRecoveryTaskTest {

    private PipelineRunRepository pipelineRunRepo;
    private NodeRunRepository nodeRunRepo;
    private SystemErrorRepository systemErrorRepo;
    private PipelineExecutionEngine engine;
    private MeterRegistry meterRegistry;
    private StalePipelineRunRecoveryTask task;

    @BeforeEach
    void setUp() {
        pipelineRunRepo = mock(PipelineRunRepository.class);
        nodeRunRepo = mock(NodeRunRepository.class);
        systemErrorRepo = mock(SystemErrorRepository.class);
        engine = mock(PipelineExecutionEngine.class);
        meterRegistry = new SimpleMeterRegistry();
        task = new StalePipelineRunRecoveryTask(
                pipelineRunRepo, nodeRunRepo, systemErrorRepo,
                providerOf(meterRegistry), engineProviderOf(engine));
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<MeterRegistry> providerOf(MeterRegistry mr) {
        ObjectProvider<MeterRegistry> p = mock(ObjectProvider.class);
        when(p.getIfAvailable()).thenReturn(mr);
        return p;
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<PipelineExecutionEngine> engineProviderOf(PipelineExecutionEngine e) {
        ObjectProvider<PipelineExecutionEngine> p = mock(ObjectProvider.class);
        when(p.getIfAvailable()).thenReturn(e);
        return p;
    }

    private double counter(String name) {
        var c = meterRegistry.find(name).counter();
        return c == null ? 0.0 : c.count();
    }

    private PipelineRun staleRun(String id, PipelineRunStatus status, int retryCount) {
        PipelineRun run = new PipelineRun();
        run.setId(id);
        run.setDocumentId("doc-" + id);
        run.setStatus(status);
        run.setCurrentNodeKey("classify-node");
        run.setCurrentNodeIndex(2);
        run.setRetryCount(retryCount);
        run.setUpdatedAt(Instant.now().minus(20, ChronoUnit.MINUTES));
        run.setStartedAt(Instant.now().minus(25, ChronoUnit.MINUTES));
        return run;
    }

    private NodeRun nodeRun(String id, NodeRunStatus status) {
        NodeRun nr = new NodeRun();
        nr.setId(id);
        nr.setStatus(status);
        nr.setStartedAt(Instant.now().minus(20, ChronoUnit.MINUTES));
        return nr;
    }

    @Test
    void no_stale_runs_means_nothing_happens() {
        when(pipelineRunRepo.findByStatusAndUpdatedAtBefore(any(), any())).thenReturn(List.of());

        task.recoverStalePipelineRuns();

        verify(pipelineRunRepo, never()).save(any());
        verify(nodeRunRepo, never()).save(any());
        verify(systemErrorRepo, never()).save(any());
        verify(engine, never()).resumeRun(any());
        assertThat(counter("pipeline.stale.detected")).isZero();
    }

    @Test
    void stale_run_under_max_retries_with_engine_present_is_resumed() {
        PipelineRun run = staleRun("run-1", PipelineRunStatus.RUNNING, 0);
        NodeRun running = nodeRun("nr-1", NodeRunStatus.RUNNING);

        when(pipelineRunRepo.findByStatusAndUpdatedAtBefore(eq(PipelineRunStatus.RUNNING), any()))
                .thenReturn(List.of(run));
        when(pipelineRunRepo.findByStatusAndUpdatedAtBefore(eq(PipelineRunStatus.WAITING), any()))
                .thenReturn(List.of());
        when(nodeRunRepo.findByPipelineRunIdOrderByStartedAtAsc("run-1"))
                .thenReturn(List.of(running));

        task.recoverStalePipelineRuns();

        // In-flight NodeRun marked FAILED.
        ArgumentCaptor<NodeRun> nodeCap = ArgumentCaptor.forClass(NodeRun.class);
        verify(nodeRunRepo, times(1)).save(nodeCap.capture());
        assertThat(nodeCap.getValue().getStatus()).isEqualTo(NodeRunStatus.FAILED);
        assertThat(nodeCap.getValue().getError()).startsWith("STALE_RECOVERY:");

        // Run saved with status=RUNNING and retryCount incremented (once — pre-resume).
        ArgumentCaptor<PipelineRun> runCap = ArgumentCaptor.forClass(PipelineRun.class);
        verify(pipelineRunRepo, times(1)).save(runCap.capture());
        PipelineRun saved = runCap.getValue();
        assertThat(saved.getStatus()).isEqualTo(PipelineRunStatus.RUNNING);
        assertThat(saved.getRetryCount()).isEqualTo(1);

        // Engine asked to resume.
        verify(engine, times(1)).resumeRun("run-1");

        // Audit + metrics.
        verify(systemErrorRepo, times(1)).save(any(SystemError.class));
        assertThat(counter("pipeline.stale.detected")).isEqualTo(1.0);
        assertThat(counter("pipeline.stale.resumed")).isEqualTo(1.0);
        assertThat(counter("pipeline.stale.failed")).isEqualTo(0.0);
        assertThat(counter("pipeline.stale.exhausted")).isEqualTo(0.0);
    }

    @Test
    void stale_run_at_max_retries_is_exhausted_and_failed_out() {
        PipelineRun run = staleRun("run-2", PipelineRunStatus.RUNNING,
                StalePipelineRunRecoveryTask.MAX_AUTO_RETRIES);

        when(pipelineRunRepo.findByStatusAndUpdatedAtBefore(eq(PipelineRunStatus.RUNNING), any()))
                .thenReturn(List.of(run));
        when(pipelineRunRepo.findByStatusAndUpdatedAtBefore(eq(PipelineRunStatus.WAITING), any()))
                .thenReturn(List.of());
        when(nodeRunRepo.findByPipelineRunIdOrderByStartedAtAsc("run-2"))
                .thenReturn(List.of());

        task.recoverStalePipelineRuns();

        ArgumentCaptor<PipelineRun> runCap = ArgumentCaptor.forClass(PipelineRun.class);
        verify(pipelineRunRepo, times(1)).save(runCap.capture());
        PipelineRun saved = runCap.getValue();
        assertThat(saved.getStatus()).isEqualTo(PipelineRunStatus.FAILED);
        assertThat(saved.getError()).contains("max auto-retries reached");
        assertThat(saved.getCompletedAt()).isNotNull();
        assertThat(saved.getTotalDurationMs()).isGreaterThan(0L);

        verify(engine, never()).resumeRun(any());
        assertThat(counter("pipeline.stale.exhausted")).isEqualTo(1.0);
        assertThat(counter("pipeline.stale.resumed")).isEqualTo(0.0);
    }

    @Test
    void engine_absent_falls_back_to_fail_out() {
        StalePipelineRunRecoveryTask noEngine = new StalePipelineRunRecoveryTask(
                pipelineRunRepo, nodeRunRepo, systemErrorRepo,
                providerOf(meterRegistry), engineProviderOf(null));

        PipelineRun run = staleRun("run-3", PipelineRunStatus.RUNNING, 0);

        when(pipelineRunRepo.findByStatusAndUpdatedAtBefore(eq(PipelineRunStatus.RUNNING), any()))
                .thenReturn(List.of(run));
        when(pipelineRunRepo.findByStatusAndUpdatedAtBefore(eq(PipelineRunStatus.WAITING), any()))
                .thenReturn(List.of());
        when(nodeRunRepo.findByPipelineRunIdOrderByStartedAtAsc("run-3"))
                .thenReturn(List.of());

        noEngine.recoverStalePipelineRuns();

        ArgumentCaptor<PipelineRun> runCap = ArgumentCaptor.forClass(PipelineRun.class);
        verify(pipelineRunRepo, times(1)).save(runCap.capture());
        assertThat(runCap.getValue().getStatus()).isEqualTo(PipelineRunStatus.FAILED);
        assertThat(runCap.getValue().getError()).contains("engine unavailable");
        verify(engine, never()).resumeRun(any());
        assertThat(counter("pipeline.stale.failed")).isEqualTo(1.0);
        assertThat(counter("pipeline.stale.exhausted")).isEqualTo(0.0);
    }

    @Test
    void engine_resumeRun_throwing_leaves_run_to_be_retried_next_cycle() {
        PipelineRun run = staleRun("run-4", PipelineRunStatus.RUNNING, 0);

        when(pipelineRunRepo.findByStatusAndUpdatedAtBefore(eq(PipelineRunStatus.RUNNING), any()))
                .thenReturn(List.of(run));
        when(pipelineRunRepo.findByStatusAndUpdatedAtBefore(eq(PipelineRunStatus.WAITING), any()))
                .thenReturn(List.of());
        when(nodeRunRepo.findByPipelineRunIdOrderByStartedAtAsc("run-4"))
                .thenReturn(List.of());
        doThrow(new RuntimeException("engine boom")).when(engine).resumeRun("run-4");

        task.recoverStalePipelineRuns();

        // Run was saved with RUNNING + retryCount incremented before the engine was called.
        verify(pipelineRunRepo, times(1)).save(any(PipelineRun.class));
        verify(engine, times(1)).resumeRun("run-4");
        // Counted as detected only — neither resumed, failed, nor exhausted.
        assertThat(counter("pipeline.stale.detected")).isEqualTo(1.0);
        assertThat(counter("pipeline.stale.resumed")).isEqualTo(0.0);
        assertThat(counter("pipeline.stale.failed")).isEqualTo(0.0);
        assertThat(counter("pipeline.stale.exhausted")).isEqualTo(0.0);
    }

    @Test
    void completed_NodeRuns_are_left_alone() {
        PipelineRun run = staleRun("run-5", PipelineRunStatus.RUNNING, 0);
        NodeRun ok = nodeRun("nr-ok", NodeRunStatus.SUCCEEDED);
        NodeRun skipped = nodeRun("nr-sk", NodeRunStatus.SKIPPED);
        NodeRun alreadyFailed = nodeRun("nr-fa", NodeRunStatus.FAILED);

        when(pipelineRunRepo.findByStatusAndUpdatedAtBefore(eq(PipelineRunStatus.RUNNING), any()))
                .thenReturn(List.of(run));
        when(pipelineRunRepo.findByStatusAndUpdatedAtBefore(eq(PipelineRunStatus.WAITING), any()))
                .thenReturn(List.of());
        when(nodeRunRepo.findByPipelineRunIdOrderByStartedAtAsc("run-5"))
                .thenReturn(List.of(ok, skipped, alreadyFailed));

        task.recoverStalePipelineRuns();

        verify(nodeRunRepo, never()).save(any());
        verify(pipelineRunRepo, times(1)).save(any());
        verify(engine, times(1)).resumeRun("run-5");
    }

    @Test
    void multiple_stale_runs_each_processed_independently() {
        PipelineRun a = staleRun("run-a", PipelineRunStatus.RUNNING, 0);
        PipelineRun b = staleRun("run-b", PipelineRunStatus.RUNNING, 0);

        when(pipelineRunRepo.findByStatusAndUpdatedAtBefore(eq(PipelineRunStatus.RUNNING), any()))
                .thenReturn(List.of(a, b));
        when(pipelineRunRepo.findByStatusAndUpdatedAtBefore(eq(PipelineRunStatus.WAITING), any()))
                .thenReturn(List.of());
        when(nodeRunRepo.findByPipelineRunIdOrderByStartedAtAsc(any())).thenReturn(List.of());

        task.recoverStalePipelineRuns();

        verify(pipelineRunRepo, times(2)).save(any());
        verify(engine, times(1)).resumeRun("run-a");
        verify(engine, times(1)).resumeRun("run-b");
        assertThat(counter("pipeline.stale.detected")).isEqualTo(2.0);
        assertThat(counter("pipeline.stale.resumed")).isEqualTo(2.0);
    }

    @Test
    void top_level_exception_is_recorded_as_SystemError_and_not_rethrown() {
        when(pipelineRunRepo.findByStatusAndUpdatedAtBefore(any(), any()))
                .thenThrow(new RuntimeException("mongo offline"));

        task.recoverStalePipelineRuns();

        verify(systemErrorRepo, atLeastOnce()).save(any(SystemError.class));
    }

    @Test
    void absent_MeterRegistry_does_not_break_the_task() {
        StalePipelineRunRecoveryTask noMetrics = new StalePipelineRunRecoveryTask(
                pipelineRunRepo, nodeRunRepo, systemErrorRepo,
                providerOf(null), engineProviderOf(engine));
        PipelineRun run = staleRun("run-x", PipelineRunStatus.RUNNING, 0);

        when(pipelineRunRepo.findByStatusAndUpdatedAtBefore(eq(PipelineRunStatus.RUNNING), any()))
                .thenReturn(List.of(run));
        when(pipelineRunRepo.findByStatusAndUpdatedAtBefore(eq(PipelineRunStatus.WAITING), any()))
                .thenReturn(List.of());
        when(nodeRunRepo.findByPipelineRunIdOrderByStartedAtAsc("run-x")).thenReturn(List.of());

        noMetrics.recoverStalePipelineRuns();

        verify(pipelineRunRepo, times(1)).save(any());
        verify(engine, times(1)).resumeRun("run-x");
    }
}
