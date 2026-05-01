package co.uk.wolfnotsheep.infrastructure.controllers.admin;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class MetricsDashboardControllerTest {

    private final MeterRegistry registry = new SimpleMeterRegistry();
    private final MetricsDashboardController controller = new MetricsDashboardController(
            registry,
            new co.uk.wolfnotsheep.infrastructure.services.CrossServiceMetricsProbe(
                    "http://router-test:0", "http://llm-test:0",
                    "http://bert-test:0", "http://slm-test:0",
                    "http://enf-test:0", "http://idx-test:0",
                    "http://audit-test:0",
                    "http://archive-test:0", "http://ocr-test:0", "http://audio-test:0"));

    @Test
    void emptyRegistry_returnsZeroedSummaryAndEmptyLists() {
        ResponseEntity<MetricsDashboardController.DashboardResponse> resp = controller.dashboard();
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();

        MetricsDashboardController.DashboardResponse body = resp.getBody();
        assertThat(body).isNotNull();
        assertThat(body.timestamp()).isNotNull();
        assertThat(body.stalePipelineDetectionAge().count()).isZero();
        assertThat(body.connectorLocks()).isEmpty();
        assertThat(body.schedulerLocks()).isEmpty();
        assertThat(body.dlqReplay()).isEmpty();
    }

    @Test
    void stalePipelineSection_pullsCountAndPercentilesFromDistributionSummary() {
        DistributionSummary summary = DistributionSummary.builder("pipeline.stale.detected.age")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
        for (int i = 1; i <= 100; i++) summary.record(i);

        MetricsDashboardController.SummaryStats stats =
                controller.dashboard().getBody().stalePipelineDetectionAge();

        assertThat(stats.count()).isEqualTo(100L);
        assertThat(stats.mean()).isBetween(40.0, 60.0);
        assertThat(stats.max()).isGreaterThanOrEqualTo(99.0);
        assertThat(stats.p50()).isPositive();
        assertThat(stats.p95()).isGreaterThan(stats.p50());
        assertThat(stats.p99()).isGreaterThanOrEqualTo(stats.p95());
    }

    @Test
    void connectorLocksSection_groupsByCounterAndTimerSource() {
        registry.counter("connector.lock.acquired", "source", "drive").increment(7);
        registry.counter("connector.lock.acquired", "source", "gmail").increment(3);
        registry.counter("connector.lock.skipped", "source", "drive").increment(2);
        registry.timer("connector.lock.action.duration", "source", "drive")
                .record(50, TimeUnit.MILLISECONDS);
        registry.timer("connector.lock.action.duration", "source", "drive")
                .record(150, TimeUnit.MILLISECONDS);

        List<MetricsDashboardController.TaggedCounter> rows =
                controller.dashboard().getBody().connectorLocks();

        assertThat(rows).hasSize(2);
        Map<String, Map<String, Double>> bySource = byTagValue(rows);
        assertThat(bySource.get("drive"))
                .containsEntry("acquired", 7.0)
                .containsEntry("skipped", 2.0);
        assertThat(bySource.get("drive").get("meanDurationMs"))
                .isBetween(50.0, 150.0);
        assertThat(bySource.get("gmail"))
                .containsEntry("acquired", 3.0);
    }

    @Test
    void schedulerLocksSection_groupsByLockNameAndOutcome() {
        registry.counter("scheduler.lock", "name", "audit-tier1-leader", "outcome", "acquired").increment(5);
        registry.counter("scheduler.lock", "name", "audit-tier1-leader", "outcome", "skipped").increment(12);
        registry.counter("scheduler.lock", "name", "stale-pipeline-recovery", "outcome", "acquired").increment(2);

        List<MetricsDashboardController.TaggedCounter> rows =
                controller.dashboard().getBody().schedulerLocks();

        Map<String, Map<String, Double>> byName = byTagValue(rows);
        assertThat(byName.get("audit-tier1-leader"))
                .containsEntry("acquired", 5.0)
                .containsEntry("skipped", 12.0);
        assertThat(byName.get("stale-pipeline-recovery"))
                .containsEntry("acquired", 2.0);
    }

    @Test
    void dlqReplaySection_groupsByQueueModeOutcome() {
        registry.counter("dlq.replay",
                "queue", "gls.documents.dlq", "mode", "real", "outcome", "replayed").increment(4);
        registry.counter("dlq.replay",
                "queue", "gls.documents.dlq", "mode", "dry_run", "outcome", "replayed").increment(2);
        registry.counter("dlq.replay",
                "queue", "gls.pipeline.dlq", "mode", "real", "outcome", "skipped").increment(1);

        List<MetricsDashboardController.DlqReplayActivity> rows =
                controller.dashboard().getBody().dlqReplay();
        assertThat(rows).hasSize(2);

        MetricsDashboardController.DlqReplayActivity docs = rows.stream()
                .filter(r -> r.queue().equals("gls.documents.dlq")).findFirst().orElseThrow();
        assertThat(docs.byMode().get("real")).containsEntry("replayed", 4.0);
        assertThat(docs.byMode().get("dry_run")).containsEntry("replayed", 2.0);

        MetricsDashboardController.DlqReplayActivity pipe = rows.stream()
                .filter(r -> r.queue().equals("gls.pipeline.dlq")).findFirst().orElseThrow();
        assertThat(pipe.byMode().get("real")).containsEntry("skipped", 1.0);
    }

    @Test
    void trackedMetricNames_includesAllSources() {
        assertThat(MetricsDashboardController.trackedMetricNames())
                .containsExactlyInAnyOrder(
                        "pipeline.stale.detected.age",
                        "connector.lock.acquired",
                        "connector.lock.skipped",
                        "connector.lock.action.duration",
                        "scheduler.lock",
                        "dlq.replay");
    }

    private static Map<String, Map<String, Double>> byTagValue(
            List<MetricsDashboardController.TaggedCounter> rows) {
        return rows.stream().collect(java.util.stream.Collectors.toMap(
                MetricsDashboardController.TaggedCounter::tagValue,
                MetricsDashboardController.TaggedCounter::values));
    }
}
