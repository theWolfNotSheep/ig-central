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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StalePipelineRunRecoveryTaskTest {

    private PipelineRunRepository pipelineRunRepo;
    private NodeRunRepository nodeRunRepo;
    private SystemErrorRepository systemErrorRepo;
    private MeterRegistry meterRegistry;
    private StalePipelineRunRecoveryTask task;

    @BeforeEach
    void setUp() {
        pipelineRunRepo = mock(PipelineRunRepository.class);
        nodeRunRepo = mock(NodeRunRepository.class);
        systemErrorRepo = mock(SystemErrorRepository.class);
        meterRegistry = new SimpleMeterRegistry();
        task = new StalePipelineRunRecoveryTask(
                pipelineRunRepo, nodeRunRepo, systemErrorRepo, providerOf(meterRegistry));
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<MeterRegistry> providerOf(MeterRegistry mr) {
        ObjectProvider<MeterRegistry> p = mock(ObjectProvider.class);
        when(p.getIfAvailable()).thenReturn(mr);
        return p;
    }

    private double counter(String name) {
        var c = meterRegistry.find(name).counter();
        return c == null ? 0.0 : c.count();
    }

    private PipelineRun staleRun(String id, PipelineRunStatus status) {
        PipelineRun run = new PipelineRun();
        run.setId(id);
        run.setDocumentId("doc-" + id);
        run.setStatus(status);
        run.setCurrentNodeKey("classify-node");
        run.setCurrentNodeIndex(2);
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
        assertThat(counter("pipeline.stale.detected")).isZero();
    }

    @Test
    void stale_RUNNING_run_marked_FAILED_with_in_flight_NodeRun_also_failed() {
        PipelineRun run = staleRun("run-1", PipelineRunStatus.RUNNING);
        NodeRun running = nodeRun("nr-1", NodeRunStatus.RUNNING);
        NodeRun succeeded = nodeRun("nr-0", NodeRunStatus.SUCCEEDED);

        when(pipelineRunRepo.findByStatusAndUpdatedAtBefore(eq(PipelineRunStatus.RUNNING), any()))
                .thenReturn(List.of(run));
        when(pipelineRunRepo.findByStatusAndUpdatedAtBefore(eq(PipelineRunStatus.WAITING), any()))
                .thenReturn(List.of());
        when(nodeRunRepo.findByPipelineRunIdOrderByStartedAtAsc("run-1"))
                .thenReturn(List.of(succeeded, running));

        task.recoverStalePipelineRuns();

        ArgumentCaptor<PipelineRun> runCap = ArgumentCaptor.forClass(PipelineRun.class);
        verify(pipelineRunRepo, times(1)).save(runCap.capture());
        PipelineRun saved = runCap.getValue();
        assertThat(saved.getStatus()).isEqualTo(PipelineRunStatus.FAILED);
        assertThat(saved.getError()).startsWith("STALE_RECOVERY:");
        assertThat(saved.getErrorNodeKey()).isEqualTo("classify-node");
        assertThat(saved.getCompletedAt()).isNotNull();
        assertThat(saved.getTotalDurationMs()).isGreaterThan(0L);

        ArgumentCaptor<NodeRun> nodeCap = ArgumentCaptor.forClass(NodeRun.class);
        verify(nodeRunRepo, times(1)).save(nodeCap.capture());
        NodeRun savedNode = nodeCap.getValue();
        assertThat(savedNode.getId()).isEqualTo("nr-1");
        assertThat(savedNode.getStatus()).isEqualTo(NodeRunStatus.FAILED);
        assertThat(savedNode.getError()).startsWith("STALE_RECOVERY:");
        assertThat(savedNode.getCompletedAt()).isNotNull();
        assertThat(savedNode.getDurationMs()).isGreaterThan(0L);

        verify(systemErrorRepo, times(1)).save(any(SystemError.class));
        assertThat(counter("pipeline.stale.detected")).isEqualTo(1.0);
        assertThat(counter("pipeline.stale.failed")).isEqualTo(1.0);
    }

    @Test
    void stale_WAITING_run_also_handled() {
        PipelineRun run = staleRun("run-2", PipelineRunStatus.WAITING);
        NodeRun waiting = nodeRun("nr-w", NodeRunStatus.WAITING);

        when(pipelineRunRepo.findByStatusAndUpdatedAtBefore(eq(PipelineRunStatus.RUNNING), any()))
                .thenReturn(List.of());
        when(pipelineRunRepo.findByStatusAndUpdatedAtBefore(eq(PipelineRunStatus.WAITING), any()))
                .thenReturn(List.of(run));
        when(nodeRunRepo.findByPipelineRunIdOrderByStartedAtAsc("run-2"))
                .thenReturn(List.of(waiting));

        task.recoverStalePipelineRuns();

        ArgumentCaptor<NodeRun> nodeCap = ArgumentCaptor.forClass(NodeRun.class);
        verify(nodeRunRepo, times(1)).save(nodeCap.capture());
        assertThat(nodeCap.getValue().getStatus()).isEqualTo(NodeRunStatus.FAILED);

        ArgumentCaptor<PipelineRun> runCap = ArgumentCaptor.forClass(PipelineRun.class);
        verify(pipelineRunRepo, times(1)).save(runCap.capture());
        assertThat(runCap.getValue().getStatus()).isEqualTo(PipelineRunStatus.FAILED);
    }

    @Test
    void completed_NodeRuns_are_left_alone() {
        PipelineRun run = staleRun("run-3", PipelineRunStatus.RUNNING);
        NodeRun ok = nodeRun("nr-ok", NodeRunStatus.SUCCEEDED);
        NodeRun skipped = nodeRun("nr-sk", NodeRunStatus.SKIPPED);
        NodeRun alreadyFailed = nodeRun("nr-fa", NodeRunStatus.FAILED);

        when(pipelineRunRepo.findByStatusAndUpdatedAtBefore(eq(PipelineRunStatus.RUNNING), any()))
                .thenReturn(List.of(run));
        when(pipelineRunRepo.findByStatusAndUpdatedAtBefore(eq(PipelineRunStatus.WAITING), any()))
                .thenReturn(List.of());
        when(nodeRunRepo.findByPipelineRunIdOrderByStartedAtAsc("run-3"))
                .thenReturn(List.of(ok, skipped, alreadyFailed));

        task.recoverStalePipelineRuns();

        verify(nodeRunRepo, never()).save(any());
        verify(pipelineRunRepo, times(1)).save(any());
    }

    @Test
    void multiple_stale_runs_each_processed_independently() {
        PipelineRun a = staleRun("run-a", PipelineRunStatus.RUNNING);
        PipelineRun b = staleRun("run-b", PipelineRunStatus.RUNNING);

        when(pipelineRunRepo.findByStatusAndUpdatedAtBefore(eq(PipelineRunStatus.RUNNING), any()))
                .thenReturn(List.of(a, b));
        when(pipelineRunRepo.findByStatusAndUpdatedAtBefore(eq(PipelineRunStatus.WAITING), any()))
                .thenReturn(List.of());
        when(nodeRunRepo.findByPipelineRunIdOrderByStartedAtAsc(any())).thenReturn(List.of());

        task.recoverStalePipelineRuns();

        verify(pipelineRunRepo, times(2)).save(any());
        verify(systemErrorRepo, times(2)).save(any());
        assertThat(counter("pipeline.stale.detected")).isEqualTo(2.0);
        assertThat(counter("pipeline.stale.failed")).isEqualTo(2.0);
    }

    @Test
    void persistence_failure_on_one_run_does_not_break_the_batch() {
        PipelineRun a = staleRun("run-a", PipelineRunStatus.RUNNING);
        PipelineRun b = staleRun("run-b", PipelineRunStatus.RUNNING);

        when(pipelineRunRepo.findByStatusAndUpdatedAtBefore(eq(PipelineRunStatus.RUNNING), any()))
                .thenReturn(List.of(a, b));
        when(pipelineRunRepo.findByStatusAndUpdatedAtBefore(eq(PipelineRunStatus.WAITING), any()))
                .thenReturn(List.of());
        when(nodeRunRepo.findByPipelineRunIdOrderByStartedAtAsc(any())).thenReturn(List.of());

        when(pipelineRunRepo.save(any(PipelineRun.class)))
                .thenThrow(new RuntimeException("mongo down"))
                .thenAnswer(inv -> inv.getArgument(0));

        task.recoverStalePipelineRuns();

        // both runs processed; one save failed, one succeeded
        verify(pipelineRunRepo, times(2)).save(any());
        assertThat(counter("pipeline.stale.detected")).isEqualTo(2.0);
        assertThat(counter("pipeline.stale.failed")).isEqualTo(1.0);
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
                pipelineRunRepo, nodeRunRepo, systemErrorRepo, providerOf(null));
        PipelineRun run = staleRun("run-x", PipelineRunStatus.RUNNING);

        when(pipelineRunRepo.findByStatusAndUpdatedAtBefore(eq(PipelineRunStatus.RUNNING), any()))
                .thenReturn(List.of(run));
        when(pipelineRunRepo.findByStatusAndUpdatedAtBefore(eq(PipelineRunStatus.WAITING), any()))
                .thenReturn(List.of());
        when(nodeRunRepo.findByPipelineRunIdOrderByStartedAtAsc("run-x")).thenReturn(List.of());

        noMetrics.recoverStalePipelineRuns();

        verify(pipelineRunRepo, times(1)).save(any());
    }
}
