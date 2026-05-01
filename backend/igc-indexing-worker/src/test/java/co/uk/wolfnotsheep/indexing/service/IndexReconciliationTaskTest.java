package co.uk.wolfnotsheep.indexing.service;

import co.uk.wolfnotsheep.document.models.DocumentStatus;
import co.uk.wolfnotsheep.document.repositories.DocumentRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IndexReconciliationTaskTest {

    private DocumentRepository documentRepo;
    private IndexingService indexingService;
    private MeterRegistry meterRegistry;
    private IndexReconciliationTask task;

    @BeforeEach
    void setUp() {
        documentRepo = mock(DocumentRepository.class);
        indexingService = mock(IndexingService.class);
        meterRegistry = new SimpleMeterRegistry();
        task = newTask(/* drift */ 10, /* autoFix */ false);
    }

    private IndexReconciliationTask newTask(long driftThreshold, boolean autoFix) {
        return new IndexReconciliationTask(
                documentRepo, indexingService, "http://es-stub:9200",
                driftThreshold, autoFix, providerOf(meterRegistry));
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<MeterRegistry> providerOf(MeterRegistry mr) {
        ObjectProvider<MeterRegistry> p = mock(ObjectProvider.class);
        when(p.getIfAvailable()).thenReturn(mr);
        return p;
    }

    private double counter(String name, String outcomeTag) {
        var c = meterRegistry.find(name).tag("outcome", outcomeTag).counter();
        return c == null ? 0.0 : c.count();
    }

    private double counter(String name) {
        var c = meterRegistry.find(name).counter();
        return c == null ? 0.0 : c.count();
    }

    @Test
    void parseCountFromResponse_extracts_es_count_field() {
        assertThat(IndexReconciliationTask.parseCountFromResponse(
                "{\"count\":42,\"_shards\":{\"total\":1}}"))
                .isEqualTo(42L);
        assertThat(IndexReconciliationTask.parseCountFromResponse(
                "{\"count\": 7,\"_shards\":{}}"))
                .isEqualTo(7L);
        assertThat(IndexReconciliationTask.parseCountFromResponse(
                "{\"count\":0}")).isZero();
    }

    @Test
    void parseCountFromResponse_returns_zero_for_malformed_or_empty() {
        assertThat(IndexReconciliationTask.parseCountFromResponse(null)).isZero();
        assertThat(IndexReconciliationTask.parseCountFromResponse("{}")).isZero();
        assertThat(IndexReconciliationTask.parseCountFromResponse("not-json")).isZero();
        assertThat(IndexReconciliationTask.parseCountFromResponse("{\"count\":}")).isZero();
    }

    @Test
    void clean_state_logs_clean_outcome_no_autofix() {
        task.handleResult(new IndexReconciliationTask.ReconciliationResult(100, 95, 5));

        assertThat(counter("index.reconciliation.runs", "clean")).isEqualTo(1.0);
        assertThat(counter("index.reconciliation.runs", "drift")).isZero();
        verify(indexingService, never()).reindexAll(any());
    }

    @Test
    void drift_above_threshold_records_drift_but_does_not_autofix_when_disabled() {
        task.handleResult(new IndexReconciliationTask.ReconciliationResult(100, 50, 50));

        assertThat(counter("index.reconciliation.runs", "drift")).isEqualTo(1.0);
        assertThat(counter("index.reconciliation.runs", "clean")).isZero();
        verify(indexingService, never()).reindexAll(any());
    }

    @Test
    void positive_drift_with_autofix_calls_reindexAll() {
        IndexReconciliationTask autoFixTask = newTask(10, true);
        when(indexingService.reindexAll(eq(null)))
                .thenReturn(new IndexingService.ReindexSummary(100, 50, 0, 0, 1234L));

        autoFixTask.handleResult(new IndexReconciliationTask.ReconciliationResult(100, 50, 50));

        verify(indexingService, times(1)).reindexAll(eq(null));
        assertThat(counter("index.reconciliation.fixes_triggered")).isEqualTo(1.0);
        assertThat(counter("index.reconciliation.runs", "drift")).isEqualTo(1.0);
    }

    @Test
    void negative_drift_with_autofix_does_not_reindex_logs_loudly() {
        IndexReconciliationTask autoFixTask = newTask(10, true);

        autoFixTask.handleResult(new IndexReconciliationTask.ReconciliationResult(50, 100, -50));

        verify(indexingService, never()).reindexAll(any());
        assertThat(counter("index.reconciliation.fixes_triggered")).isZero();
        assertThat(counter("index.reconciliation.runs", "drift")).isEqualTo(1.0);
    }

    @Test
    void exception_during_autofix_reindex_is_caught_and_logged() {
        IndexReconciliationTask autoFixTask = newTask(10, true);
        when(indexingService.reindexAll(any()))
                .thenThrow(new RuntimeException("ES gave up"));

        // Must not throw — the scheduled task should never bubble up.
        autoFixTask.handleResult(new IndexReconciliationTask.ReconciliationResult(100, 0, 100));

        assertThat(counter("index.reconciliation.runs", "drift")).isEqualTo(1.0);
        // Counter not incremented because reindex threw.
        assertThat(counter("index.reconciliation.fixes_triggered")).isZero();
    }

    @Test
    void surveyOnce_uses_count_minus_disposed_for_mongo_total() {
        when(documentRepo.count()).thenReturn(150L);
        when(documentRepo.countByStatus(DocumentStatus.DISPOSED)).thenReturn(50L);

        // We can't easily test queryEsCount here without an HTTP server;
        // verify that the count() / countByStatus() calls are correct by
        // catching the IllegalStateException raised when ES is unreachable
        // (the http://es-stub:9200 host doesn't resolve in tests).
        try {
            task.surveyOnce();
        } catch (IllegalStateException expected) {
            assertThat(expected.getMessage()).contains("es count failed");
        }

        verify(documentRepo, times(1)).count();
        verify(documentRepo, times(1)).countByStatus(DocumentStatus.DISPOSED);
    }

    @Test
    void delta_at_exactly_threshold_is_treated_as_clean() {
        task.handleResult(new IndexReconciliationTask.ReconciliationResult(100, 90, 10));

        assertThat(counter("index.reconciliation.runs", "clean")).isEqualTo(1.0);
        assertThat(counter("index.reconciliation.runs", "drift")).isZero();
    }

    @Test
    void absent_MeterRegistry_does_not_break_handleResult() {
        IndexReconciliationTask noMetrics = new IndexReconciliationTask(
                documentRepo, indexingService, "http://es-stub:9200",
                10, true, providerOf(null));
        when(indexingService.reindexAll(any()))
                .thenReturn(new IndexingService.ReindexSummary(50, 50, 0, 0, 100L));

        noMetrics.handleResult(new IndexReconciliationTask.ReconciliationResult(100, 50, 50));

        verify(indexingService, times(1)).reindexAll(any());
    }
}
