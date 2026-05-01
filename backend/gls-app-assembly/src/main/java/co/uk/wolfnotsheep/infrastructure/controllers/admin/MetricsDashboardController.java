package co.uk.wolfnotsheep.infrastructure.controllers.admin;

import co.uk.wolfnotsheep.infrastructure.services.CrossServiceMetricsProbe;
import co.uk.wolfnotsheep.infrastructure.services.CrossServiceMetricsProbe.ServiceProbeResult;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;
import io.micrometer.core.instrument.distribution.ValueAtPercentile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

/**
 * Phase 3 PR2 — performance-dashboard data source.
 *
 * <p>Aggregates the Phase 2 Micrometer metrics that are local to
 * {@code gls-app-assembly} (or shipped through {@code gls-platform-audit}'s
 * lock provider) into a UI-friendly JSON shape. The frontend's
 * Performance dashboard reads {@code GET /api/admin/metrics/dashboard}
 * and renders the result with recharts.
 *
 * <p>Cross-service metrics ({@code gls_router_classify_*},
 * {@code llm.circuit_breaker.*}, {@code llm.fallback.invocations},
 * etc.) live on those services' own registries; surfacing them here
 * needs an HTTP probe layer (future PR). This endpoint is scoped to
 * what's locally observable.
 *
 * <p>Returns cumulative values — Prometheus is the source-of-truth for
 * time-series. Operators read this for current state at a glance;
 * historical graphs come from the Prometheus scrape.
 */
@RestController
@RequestMapping("/api/admin/metrics")
public class MetricsDashboardController {

    private static final Logger log = LoggerFactory.getLogger(MetricsDashboardController.class);

    private final MeterRegistry meterRegistry;
    private final CrossServiceMetricsProbe crossServiceProbe;

    public MetricsDashboardController(MeterRegistry meterRegistry,
                                      CrossServiceMetricsProbe crossServiceProbe) {
        this.meterRegistry = meterRegistry;
        this.crossServiceProbe = crossServiceProbe;
    }

    @GetMapping("/dashboard")
    public ResponseEntity<DashboardResponse> dashboard() {
        try {
            return ResponseEntity.ok(new DashboardResponse(
                    Instant.now(),
                    stalePipelineSection(),
                    connectorLocksSection(),
                    schedulerLocksSection(),
                    dlqReplaySection()));
        } catch (RuntimeException e) {
            log.warn("metrics dashboard build failed: {}", e.getMessage(), e);
            return ResponseEntity.ok(new DashboardResponse(Instant.now(),
                    new SummaryStats(0, 0, 0, 0, 0, 0),
                    List.of(), List.of(), List.of()));
        }
    }

    /**
     * Cross-service probe results — scrapes peer services'
     * {@code /actuator/prometheus} and surfaces a curated set of metrics
     * (router decisions, LLM circuit breaker, fallback invocations,
     * rate-limit gates). Best-effort: failures per-service surface as
     * {@code reachable=false} entries rather than 5xx-ing the whole
     * response.
     */
    @GetMapping("/dashboard/cross-service")
    public ResponseEntity<CrossServiceResponse> crossService() {
        List<ServiceProbeResult> probes = crossServiceProbe.probeAll();
        return ResponseEntity.ok(new CrossServiceResponse(Instant.now(), probes));
    }

    private SummaryStats stalePipelineSection() {
        DistributionSummary summary = meterRegistry.find("pipeline.stale.detected.age").summary();
        long count = 0L;
        double mean = 0.0, max = 0.0, p50 = 0.0, p95 = 0.0, p99 = 0.0;
        if (summary != null) {
            HistogramSnapshot snap = summary.takeSnapshot();
            count = snap.count();
            mean = snap.mean();
            max = snap.max();
            for (ValueAtPercentile vp : snap.percentileValues()) {
                if (Math.abs(vp.percentile() - 0.5) < 1e-9) p50 = vp.value();
                else if (Math.abs(vp.percentile() - 0.95) < 1e-9) p95 = vp.value();
                else if (Math.abs(vp.percentile() - 0.99) < 1e-9) p99 = vp.value();
            }
        }
        return new SummaryStats(count, mean, max, p50, p95, p99);
    }

    private List<TaggedCounter> connectorLocksSection() {
        Map<String, Map<String, Double>> bySource = new TreeMap<>();
        for (Counter c : findCountersByName("connector.lock.acquired")) {
            String src = tagValue(c.getId(), "source");
            bySource.computeIfAbsent(src, k -> new LinkedHashMap<>()).put("acquired", c.count());
        }
        for (Counter c : findCountersByName("connector.lock.skipped")) {
            String src = tagValue(c.getId(), "source");
            bySource.computeIfAbsent(src, k -> new LinkedHashMap<>()).put("skipped", c.count());
        }
        // Action-duration timer: mean ms per source
        for (Timer t : findTimersByName("connector.lock.action.duration")) {
            String src = tagValue(t.getId(), "source");
            double meanMs = t.mean(TimeUnit.MILLISECONDS);
            bySource.computeIfAbsent(src, k -> new LinkedHashMap<>()).put("meanDurationMs", meanMs);
        }
        List<TaggedCounter> out = new ArrayList<>();
        for (Map.Entry<String, Map<String, Double>> e : bySource.entrySet()) {
            out.add(new TaggedCounter("source", e.getKey(), e.getValue()));
        }
        return out;
    }

    private List<TaggedCounter> schedulerLocksSection() {
        Map<String, Map<String, Double>> byName = new TreeMap<>();
        for (Counter c : findCountersByName("scheduler.lock")) {
            String name = tagValue(c.getId(), "name");
            String outcome = tagValue(c.getId(), "outcome");
            byName.computeIfAbsent(name, k -> new LinkedHashMap<>()).put(outcome, c.count());
        }
        List<TaggedCounter> out = new ArrayList<>();
        for (Map.Entry<String, Map<String, Double>> e : byName.entrySet()) {
            out.add(new TaggedCounter("name", e.getKey(), e.getValue()));
        }
        return out;
    }

    private List<DlqReplayActivity> dlqReplaySection() {
        Map<String, Map<String, Map<String, Double>>> byQueue = new TreeMap<>();
        for (Counter c : findCountersByName("dlq.replay")) {
            String queue = tagValue(c.getId(), "queue");
            String outcome = tagValue(c.getId(), "outcome");
            String mode = tagValue(c.getId(), "mode");
            byQueue
                    .computeIfAbsent(queue, k -> new LinkedHashMap<>())
                    .computeIfAbsent(mode, k -> new LinkedHashMap<>())
                    .put(outcome, c.count());
        }
        List<DlqReplayActivity> out = new ArrayList<>();
        for (Map.Entry<String, Map<String, Map<String, Double>>> e : byQueue.entrySet()) {
            out.add(new DlqReplayActivity(e.getKey(), e.getValue()));
        }
        return out;
    }

    /* ── micrometer helpers ─────────────────────────────────────────── */

    private List<Counter> findCountersByName(String name) {
        List<Counter> out = new ArrayList<>();
        for (Meter m : meterRegistry.find(name).meters()) {
            if (m instanceof Counter c) out.add(c);
        }
        return out;
    }

    private List<Timer> findTimersByName(String name) {
        List<Timer> out = new ArrayList<>();
        for (Meter m : meterRegistry.find(name).meters()) {
            if (m instanceof Timer t) out.add(t);
        }
        return out;
    }

    private static String tagValue(Meter.Id id, String tagKey) {
        for (Tag t : id.getTags()) {
            if (tagKey.equals(t.getKey())) return t.getValue();
        }
        return "unknown";
    }

    /* ── Response DTOs ──────────────────────────────────────────────── */

    public record DashboardResponse(
            Instant timestamp,
            SummaryStats stalePipelineDetectionAge,
            List<TaggedCounter> connectorLocks,
            List<TaggedCounter> schedulerLocks,
            List<DlqReplayActivity> dlqReplay) {}

    /** Summary statistics for a {@link DistributionSummary} (count, mean, max, p50/p95/p99). */
    public record SummaryStats(
            long count,
            double mean,
            double max,
            double p50,
            double p95,
            double p99) {}

    /** A single tag value's family of metrics (e.g. all counters with {@code source=drive}). */
    public record TaggedCounter(String tagKey, String tagValue, Map<String, Double> values) {}

    public record DlqReplayActivity(
            String queue,
            // mode (real / dry_run) → outcome (replayed / skipped) → count
            Map<String, Map<String, Double>> byMode) {}

    public record CrossServiceResponse(
            Instant timestamp,
            List<ServiceProbeResult> services) {}

    /** Visible for testing — exposes the canonical metric names the dashboard relies on. */
    static Set<String> trackedMetricNames() {
        return Set.of(
                "pipeline.stale.detected.age",
                "connector.lock.acquired",
                "connector.lock.skipped",
                "connector.lock.action.duration",
                "scheduler.lock",
                "dlq.replay");
    }
}
