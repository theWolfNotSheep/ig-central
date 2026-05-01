package co.uk.wolfnotsheep.infrastructure.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Phase 3 PR16 — scrapes the {@code /actuator/prometheus} endpoint of
 * each peer Spring Boot service and surfaces a curated set of metrics
 * for the operator dashboard.
 *
 * <p>The Performance dashboard's existing local section reads from
 * {@code gls-app-assembly}'s own {@link io.micrometer.core.instrument.MeterRegistry}.
 * Cross-service metrics — router decisions, LLM circuit breaker state,
 * fallback invocations, rate-limit gates — live on the originating
 * services' registries and aren't reachable from this process. This
 * probe fills that gap with a periodic HTTP scrape.
 *
 * <p>Design notes:
 * <ul>
 *   <li>The scrape is best-effort. A peer being down returns an
 *       {@link ServiceProbeResult} with {@code reachable=false}, never
 *       throws. The dashboard then renders the service as "down" while
 *       still showing the others.</li>
 *   <li>Each service's URL is a {@code @Value}-injected property so
 *       Docker Compose hostnames can be overridden in different
 *       deployments without recompile.</li>
 *   <li>The metric allowlist is per-service to keep parsing focused —
 *       we don't drag every JVM/HikariCP/HTTP-client metric the peers
 *       emit, just the ones the dashboard wants.</li>
 *   <li>Per-call timeout 3s. The dashboard refreshes manually on demand,
 *       so blocking 3s × N peers on a manual refresh is acceptable.</li>
 * </ul>
 */
@Service
public class CrossServiceMetricsProbe {

    private static final Logger log = LoggerFactory.getLogger(CrossServiceMetricsProbe.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(3);

    /** Metrics the router exposes that the dashboard cares about. */
    private static final Set<String> ROUTER_METRICS = Set.of(
            "gls_router_classify_result_total",
            "gls_router_classify_tier_by_category_total",
            "gls_router_classify_cost_units_total",
            "gls_router_classify_cascade_steps",
            "router_llm_budget_exhausted_until_epoch_s",
            "router_rate_limit_permits_available",
            "router_rate_limit_permits_total");

    /** Metrics the LLM worker exposes. */
    private static final Set<String> LLM_WORKER_METRICS = Set.of(
            "llm_circuit_breaker_state",
            "llm_circuit_breaker_consecutive_failures",
            "llm_fallback_invocations_total");

    /**
     * Generic operational signals every Spring Boot service emits.
     * Useful when a peer doesn't have distinctive app-level metrics
     * yet — the dashboard still gets a "service is up + this busy"
     * indicator. Caveat: {@code http_server_requests_seconds_count}
     * has high label cardinality (uri × method × status); we accept
     * the data volume because Spring restricts the {@code uri} label
     * to the matched template, not the raw URL.
     */
    private static final Set<String> GENERIC_METRICS = Set.of(
            "process_uptime_seconds",
            "jvm_threads_live_threads",
            "http_server_requests_seconds_count");

    private static Set<String> withGeneric(Set<String> serviceSpecific) {
        Set<String> merged = new java.util.HashSet<>(GENERIC_METRICS);
        merged.addAll(serviceSpecific);
        return Set.copyOf(merged);
    }

    private final HttpClient httpClient;
    private final List<PeerService> peers;

    public CrossServiceMetricsProbe(
            @Value("${gls.metrics.probe.router-url:http://gls-classifier-router:8093}") String routerUrl,
            @Value("${gls.metrics.probe.llm-worker-url:http://gls-llm-worker:8096}") String llmWorkerUrl,
            @Value("${gls.metrics.probe.bert-inference-url:http://gls-bert-inference:8094}") String bertUrl,
            @Value("${gls.metrics.probe.slm-worker-url:http://gls-slm-worker:8095}") String slmUrl,
            @Value("${gls.metrics.probe.enforcement-worker-url:http://gls-enforcement-worker:8097}") String enforcementUrl,
            @Value("${gls.metrics.probe.indexing-worker-url:http://gls-indexing-worker:8098}") String indexingUrl,
            @Value("${gls.metrics.probe.audit-collector-url:http://gls-audit-collector:8099}") String auditUrl,
            @Value("${gls.metrics.probe.extraction-archive-url:http://gls-extraction-archive:8090}") String archiveUrl,
            @Value("${gls.metrics.probe.extraction-ocr-url:http://gls-extraction-ocr:8091}") String ocrUrl,
            @Value("${gls.metrics.probe.extraction-audio-url:http://gls-extraction-audio:8092}") String audioUrl) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
        // Order: hot-path services first (operators care about router / llm-worker
        // most), then collectors, then extraction. Helps when the dashboard
        // truncates on slow renders.
        this.peers = List.of(
                new PeerService("router", stripTrailing(routerUrl), withGeneric(ROUTER_METRICS)),
                new PeerService("llm-worker", stripTrailing(llmWorkerUrl), withGeneric(LLM_WORKER_METRICS)),
                new PeerService("bert-inference", stripTrailing(bertUrl), GENERIC_METRICS),
                new PeerService("slm-worker", stripTrailing(slmUrl), GENERIC_METRICS),
                new PeerService("enforcement-worker", stripTrailing(enforcementUrl), GENERIC_METRICS),
                new PeerService("indexing-worker", stripTrailing(indexingUrl), GENERIC_METRICS),
                new PeerService("audit-collector", stripTrailing(auditUrl), GENERIC_METRICS),
                new PeerService("extraction-archive", stripTrailing(archiveUrl), GENERIC_METRICS),
                new PeerService("extraction-ocr", stripTrailing(ocrUrl), GENERIC_METRICS),
                new PeerService("extraction-audio", stripTrailing(audioUrl), GENERIC_METRICS));
    }

    public List<ServiceProbeResult> probeAll() {
        // Probe sequentially so a slow peer doesn't pile on the thread pool.
        // If real-world latency makes this slow, swap to virtual-thread parallelism.
        List<ServiceProbeResult> out = new java.util.ArrayList<>(peers.size());
        for (PeerService peer : peers) {
            out.add(probe(peer.name(), peer.baseUrl(), peer.metrics()));
        }
        return out;
    }

    private record PeerService(String name, String baseUrl, Set<String> metrics) {}

    ServiceProbeResult probe(String service, String baseUrl, Set<String> allowlist) {
        URI uri = URI.create(baseUrl + "/actuator/prometheus");
        HttpRequest req = HttpRequest.newBuilder(uri)
                .header("Accept", "text/plain")
                .timeout(TIMEOUT)
                .GET()
                .build();
        long start = System.currentTimeMillis();
        try {
            HttpResponse<String> resp = httpClient.send(req, BodyHandlers.ofString(StandardCharsets.UTF_8));
            long elapsed = System.currentTimeMillis() - start;
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                return ServiceProbeResult.unreachable(service, baseUrl,
                        "HTTP " + resp.statusCode(), elapsed);
            }
            List<PrometheusTextParser.Sample> samples =
                    PrometheusTextParser.parse(resp.body(), allowlist);
            return ServiceProbeResult.ok(service, baseUrl, samples, elapsed);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            log.debug("metrics probe failed for {}: {}", service, e.getMessage());
            return ServiceProbeResult.unreachable(service, baseUrl, e.getMessage(),
                    System.currentTimeMillis() - start);
        }
    }

    private static String stripTrailing(String url) {
        return url == null || !url.endsWith("/") ? url : url.substring(0, url.length() - 1);
    }

    /**
     * Outcome of probing one peer. {@code reachable=false} carries the
     * underlying error in {@code error}; {@code samples} is empty on
     * failure. {@code elapsedMs} captures the wall-clock latency of the
     * probe call (for connectivity / latency dashboards).
     */
    public record ServiceProbeResult(
            String service,
            String url,
            boolean reachable,
            String error,
            long elapsedMs,
            List<PrometheusTextParser.Sample> samples) {

        public static ServiceProbeResult ok(String service, String url,
                                            List<PrometheusTextParser.Sample> samples, long elapsedMs) {
            return new ServiceProbeResult(service, url, true, null, elapsedMs, samples);
        }

        public static ServiceProbeResult unreachable(String service, String url,
                                                     String error, long elapsedMs) {
            return new ServiceProbeResult(service, url, false, error, elapsedMs, List.of());
        }

        /** Group samples by metric name → list of label/value pairs. Useful for the dashboard. */
        public Map<String, List<PrometheusTextParser.Sample>> byMetricName() {
            Map<String, List<PrometheusTextParser.Sample>> out = new LinkedHashMap<>();
            for (PrometheusTextParser.Sample s : samples) {
                out.computeIfAbsent(s.metricName(), k -> new java.util.ArrayList<>()).add(s);
            }
            return out;
        }
    }
}
