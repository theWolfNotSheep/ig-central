package co.uk.wolfnotsheep.indexing.service;

import co.uk.wolfnotsheep.document.models.DocumentStatus;
import co.uk.wolfnotsheep.document.repositories.DocumentRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Phase 2.1 PR5 — periodic ES ↔ Mongo reconciliation.
 *
 * <p>Surveys the relationship between Mongo (system of record) and the
 * {@code ig_central_documents} index, surfacing any drift. The plan
 * names this "rebuild ES from Mongo if index is corrupted or behind" —
 * the rebuild side is observe-only by default; auto-fix can be enabled
 * by setting {@code gls.indexing.reconciliation.auto-fix=true}.
 *
 * <p>Detection:
 * <ul>
 *   <li>{@code mongoCount} = non-{@link DocumentStatus#DISPOSED} document count
 *       ({@code documentRepo.count() - countByStatus(DISPOSED)}).</li>
 *   <li>{@code esCount} = {@code GET /ig_central_documents/_count}.</li>
 *   <li>{@code delta} = {@code mongoCount - esCount}. Positive = ES is
 *       behind; negative = ES has stragglers (deleted-in-Mongo not yet
 *       removed from ES). Both warrant investigation.</li>
 * </ul>
 *
 * <p>Action (when {@code auto-fix=true}):
 * <ul>
 *   <li>{@code delta > drift-threshold} (default 10) → call
 *       {@link IndexingService#reindexAll(java.util.List)} with no
 *       status filter, blasting every non-disposed document back into
 *       ES. Heavy, but matches the "rebuild from Mongo" plan wording.</li>
 *   <li>{@code delta < -drift-threshold} → log loudly and emit metric;
 *       auto-fix doesn't try to delete from ES (pruning is a separate
 *       lifecycle concern handled by {@code removeDocument}).</li>
 * </ul>
 *
 * <p>Observability:
 * <ul>
 *   <li>Gauges {@code index.reconciliation.mongo.count} and
 *       {@code index.reconciliation.es.count} hold the latest survey
 *       values; subtract for the dashboard delta panel.</li>
 *   <li>Counter {@code index.reconciliation.runs{outcome=...}} ticks
 *       per cycle (outcomes: {@code clean}, {@code drift}, {@code error}).</li>
 *   <li>Counter {@code index.reconciliation.fixes_triggered} ticks
 *       when auto-fix actually calls {@code reindexAll}.</li>
 * </ul>
 */
@Component
public class IndexReconciliationTask {

    private static final Logger log = LoggerFactory.getLogger(IndexReconciliationTask.class);

    private final DocumentRepository documentRepo;
    private final IndexingService indexingService;
    private final HttpClient httpClient;
    private final String esUri;
    private final long driftThreshold;
    private final boolean autoFix;
    private final ObjectProvider<MeterRegistry> meterRegistryProvider;

    private final AtomicLong lastMongoCount = new AtomicLong(0);
    private final AtomicLong lastEsCount = new AtomicLong(0);
    private volatile boolean gaugesRegistered = false;

    public IndexReconciliationTask(
            DocumentRepository documentRepo,
            IndexingService indexingService,
            @Value("${spring.elasticsearch.uris:http://localhost:9200}") String esUri,
            @Value("${gls.indexing.reconciliation.drift-threshold:10}") long driftThreshold,
            @Value("${gls.indexing.reconciliation.auto-fix:false}") boolean autoFix,
            ObjectProvider<MeterRegistry> meterRegistryProvider) {
        this.documentRepo = documentRepo;
        this.indexingService = indexingService;
        this.esUri = esUri;
        this.driftThreshold = driftThreshold;
        this.autoFix = autoFix;
        this.meterRegistryProvider = meterRegistryProvider;
        this.httpClient = HttpClient.newHttpClient();
    }

    /**
     * Survey + (optionally) fix. Default cadence is once per day; tune via
     * {@code gls.indexing.reconciliation.interval} (e.g. {@code PT1H} for
     * hourly). Initial-delay 5 min so app startup isn't immediately followed
     * by a heavy survey.
     */
    @Scheduled(
            fixedDelayString = "${gls.indexing.reconciliation.interval:PT24H}",
            initialDelayString = "${gls.indexing.reconciliation.initial-delay:PT5M}")
    public void reconcileIndex() {
        try {
            ensureGaugesRegistered();
            ReconciliationResult result = surveyOnce();
            handleResult(result);
        } catch (Exception e) {
            log.error("Index reconciliation failed: {}", e.getMessage(), e);
            recordOutcome("error");
        }
    }

    /** Visible for testing. */
    ReconciliationResult surveyOnce() {
        long mongoCount;
        try {
            mongoCount = documentRepo.count() - documentRepo.countByStatus(DocumentStatus.DISPOSED);
        } catch (RuntimeException e) {
            throw new IllegalStateException("mongo count failed: " + e.getMessage(), e);
        }
        long esCount;
        try {
            esCount = queryEsCount();
        } catch (RuntimeException e) {
            throw new IllegalStateException("es count failed: " + e.getMessage(), e);
        }
        lastMongoCount.set(mongoCount);
        lastEsCount.set(esCount);
        long delta = mongoCount - esCount;
        return new ReconciliationResult(mongoCount, esCount, delta);
    }

    /** Visible for testing. */
    void handleResult(ReconciliationResult result) {
        if (Math.abs(result.delta()) <= driftThreshold) {
            log.info("Index reconciliation: clean — mongo={}, es={}, delta={} (threshold={})",
                    result.mongoCount(), result.esCount(), result.delta(), driftThreshold);
            recordOutcome("clean");
            return;
        }
        log.warn("Index reconciliation: DRIFT detected — mongo={}, es={}, delta={} (threshold={})",
                result.mongoCount(), result.esCount(), result.delta(), driftThreshold);
        recordOutcome("drift");

        if (autoFix && result.delta() > 0) {
            log.warn("Index reconciliation: auto-fix enabled and ES is behind — triggering full reindex");
            try {
                IndexingService.ReindexSummary summary = indexingService.reindexAll(null);
                log.info("Index reconciliation: auto-fix complete — total={}, indexed={}, skipped={}, failed={}, durationMs={}",
                        summary.totalDocuments(), summary.indexedCount(),
                        summary.skippedCount(), summary.failedCount(), summary.durationMs());
                recordCounter("index.reconciliation.fixes_triggered");
            } catch (RuntimeException e) {
                log.error("Index reconciliation: auto-fix reindexAll failed: {}", e.getMessage(), e);
            }
        } else if (autoFix) {
            log.warn("Index reconciliation: auto-fix enabled but delta is negative ({}) — ES has stragglers, " +
                    "manual intervention required (auto-prune is not implemented)", result.delta());
        }
    }

    private long queryEsCount() {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(esUri + "/" + IndexingService.INDEX_NAME + "/_count"))
                .GET().build();
        HttpResponse<String> resp;
        try {
            resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            throw new RuntimeException("ES _count transport: " + e.getMessage(), e);
        }
        if (resp.statusCode() != 200) {
            // 404 = index doesn't exist yet (cold start) — treat as zero docs.
            if (resp.statusCode() == 404) {
                return 0L;
            }
            throw new RuntimeException("ES _count returned HTTP " + resp.statusCode());
        }
        return parseCountFromResponse(resp.body());
    }

    /** Visible for testing — parses {@code "count":N} out of an ES _count response. */
    static long parseCountFromResponse(String body) {
        if (body == null) return 0L;
        int idx = body.indexOf("\"count\":");
        if (idx < 0) return 0L;
        int start = idx + 8;
        // Skip optional whitespace.
        while (start < body.length() && Character.isWhitespace(body.charAt(start))) start++;
        int end = start;
        while (end < body.length() && Character.isDigit(body.charAt(end))) end++;
        if (end == start) return 0L;
        try {
            return Long.parseLong(body.substring(start, end));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private void ensureGaugesRegistered() {
        if (gaugesRegistered) return;
        MeterRegistry registry = meterRegistryProvider.getIfAvailable();
        if (registry == null) return;
        Gauge.builder("index.reconciliation.mongo.count", lastMongoCount, AtomicLong::get)
                .description("Most recent reconciliation survey: count of non-DISPOSED Mongo documents")
                .register(registry);
        Gauge.builder("index.reconciliation.es.count", lastEsCount, AtomicLong::get)
                .description("Most recent reconciliation survey: count of documents in the ES index")
                .register(registry);
        gaugesRegistered = true;
    }

    private void recordOutcome(String outcome) {
        MeterRegistry registry = meterRegistryProvider.getIfAvailable();
        if (registry == null) return;
        registry.counter("index.reconciliation.runs", Tags.of("outcome", outcome)).increment();
    }

    private void recordCounter(String name) {
        MeterRegistry registry = meterRegistryProvider.getIfAvailable();
        if (registry == null) return;
        registry.counter(name).increment();
    }

    public record ReconciliationResult(long mongoCount, long esCount, long delta) {}
}
