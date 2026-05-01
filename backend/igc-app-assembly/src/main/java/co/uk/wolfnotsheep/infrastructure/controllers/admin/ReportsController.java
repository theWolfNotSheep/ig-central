package co.uk.wolfnotsheep.infrastructure.controllers.admin;

import co.uk.wolfnotsheep.document.models.AiUsageLog;
import co.uk.wolfnotsheep.document.models.DocumentModel;
import co.uk.wolfnotsheep.document.repositories.AiUsageLogRepository;
import co.uk.wolfnotsheep.document.services.DocumentService;
import co.uk.wolfnotsheep.governance.models.BertTrainingJob;
import co.uk.wolfnotsheep.governance.models.ClassificationCorrection;
import co.uk.wolfnotsheep.governance.models.DocumentClassificationResult;
import co.uk.wolfnotsheep.governance.models.TrainingDataSample;
import co.uk.wolfnotsheep.governance.repositories.BertTrainingJobRepository;
import co.uk.wolfnotsheep.governance.repositories.ClassificationCorrectionRepository;
import co.uk.wolfnotsheep.governance.repositories.DocumentClassificationResultRepository;
import co.uk.wolfnotsheep.governance.repositories.TrainingDataSampleRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * ML Reports endpoints — provides aggregated data for charting
 * confidence distributions, model performance, training data health,
 * AI cost trends, and human feedback analysis.
 */
@RestController
@RequestMapping("/api/admin/reports")
public class ReportsController {

    private final DocumentClassificationResultRepository classificationRepo;
    private final BertTrainingJobRepository trainingJobRepo;
    private final TrainingDataSampleRepository trainingDataRepo;
    private final AiUsageLogRepository aiUsageRepo;
    private final ClassificationCorrectionRepository correctionRepo;
    private final DocumentService documentService;

    public ReportsController(DocumentClassificationResultRepository classificationRepo,
                             BertTrainingJobRepository trainingJobRepo,
                             TrainingDataSampleRepository trainingDataRepo,
                             AiUsageLogRepository aiUsageRepo,
                             ClassificationCorrectionRepository correctionRepo,
                             DocumentService documentService) {
        this.classificationRepo = classificationRepo;
        this.trainingJobRepo = trainingJobRepo;
        this.trainingDataRepo = trainingDataRepo;
        this.aiUsageRepo = aiUsageRepo;
        this.correctionRepo = correctionRepo;
        this.documentService = documentService;
    }

    /**
     * Confidence score distribution — histogram buckets (0-10%, 10-20%, ..., 90-100%)
     * for both BERT and LLM classifications.
     */
    @GetMapping("/confidence-distribution")
    public ResponseEntity<Map<String, Object>> confidenceDistribution() {
        List<DocumentClassificationResult> results = classificationRepo.findAll();

        String[] bucketLabels = {"0-10%", "10-20%", "20-30%", "30-40%", "40-50%",
                "50-60%", "60-70%", "70-80%", "80-90%", "90-100%"};
        int[] bertBuckets = new int[10];
        int[] llmBuckets = new int[10];

        for (var r : results) {
            int bucket = Math.min((int) (r.getConfidence() * 10), 9);
            boolean isBert = r.getTags() != null && r.getTags().contains("bert-classified");
            if (isBert) bertBuckets[bucket]++;
            else llmBuckets[bucket]++;
        }

        var buckets = new ArrayList<Map<String, Object>>();
        for (int i = 0; i < 10; i++) {
            var b = new LinkedHashMap<String, Object>();
            b.put("range", bucketLabels[i]);
            b.put("bert", bertBuckets[i]);
            b.put("llm", llmBuckets[i]);
            b.put("total", bertBuckets[i] + llmBuckets[i]);
            buckets.add(b);
        }

        var result = new LinkedHashMap<String, Object>();
        result.put("buckets", buckets);
        result.put("totalClassifications", results.size());
        return ResponseEntity.ok(result);
    }

    /**
     * Model performance over training versions — accuracy, F1, sample count per version.
     */
    @GetMapping("/model-performance")
    public ResponseEntity<Map<String, Object>> modelPerformance() {
        List<BertTrainingJob> jobs = trainingJobRepo.findAllByOrderByStartedAtDesc();
        // Reverse to chronological order
        Collections.reverse(jobs);

        var versions = new ArrayList<Map<String, Object>>();
        for (var job : jobs) {
            if (job.getStatus() != BertTrainingJob.JobStatus.COMPLETED
                    && job.getStatus() != BertTrainingJob.JobStatus.PROMOTED) continue;

            var v = new LinkedHashMap<String, Object>();
            v.put("version", job.getModelVersion());
            v.put("sampleCount", job.getSampleCount());
            v.put("categoryCount", job.getCategoryCount());
            v.put("promoted", job.isPromoted());
            v.put("startedAt", job.getStartedAt() != null ? job.getStartedAt().toString() : null);
            v.put("completedAt", job.getCompletedAt() != null ? job.getCompletedAt().toString() : null);

            Map<String, Object> metrics = job.getMetrics();
            if (metrics != null) {
                v.put("accuracy", metrics.get("accuracy"));
                v.put("f1", metrics.get("f1"));
                v.put("loss", metrics.get("loss"));
                v.put("perClass", metrics.get("per_class"));
                v.put("warnings", metrics.get("warnings"));
            } else {
                v.put("accuracy", null);
                v.put("f1", null);
            }

            versions.add(v);
        }

        return ResponseEntity.ok(Map.of("versions", versions));
    }

    /**
     * BERT hit rate over time — daily breakdown of BERT vs LLM classifications.
     */
    @GetMapping("/bert-hit-rate")
    public ResponseEntity<Map<String, Object>> bertHitRate(
            @RequestParam(defaultValue = "30") int days) {

        Instant cutoff = Instant.now().minus(days, ChronoUnit.DAYS);
        List<DocumentClassificationResult> results =
                classificationRepo.findByClassifiedAtAfterOrderByClassifiedAtAsc(cutoff);

        // Group by day
        var dailyMap = new LinkedHashMap<String, int[]>(); // [bertHits, llmHits]
        for (var r : results) {
            if (r.getClassifiedAt() == null) continue;
            String day = r.getClassifiedAt().toString().substring(0, 10);
            int[] counts = dailyMap.computeIfAbsent(day, k -> new int[2]);
            boolean isBert = r.getTags() != null && r.getTags().contains("bert-classified");
            if (isBert) counts[0]++;
            else counts[1]++;
        }

        var daily = new ArrayList<Map<String, Object>>();
        for (var entry : dailyMap.entrySet()) {
            var d = new LinkedHashMap<String, Object>();
            d.put("date", entry.getKey());
            d.put("bert", entry.getValue()[0]);
            d.put("llm", entry.getValue()[1]);
            int total = entry.getValue()[0] + entry.getValue()[1];
            d.put("total", total);
            d.put("hitRate", total > 0 ? Math.round(entry.getValue()[0] * 1000.0 / total) / 10.0 : 0);
            daily.add(d);
        }

        return ResponseEntity.ok(Map.of("daily", daily, "days", days));
    }

    /**
     * Training data health — samples per category, by source, graduation progress.
     */
    @GetMapping("/training-data")
    public ResponseEntity<Map<String, Object>> trainingData() {
        List<TrainingDataSample> samples = trainingDataRepo.findAll();

        // Per-category breakdown by source
        var categoryData = new LinkedHashMap<String, Map<String, Long>>();
        for (var s : samples) {
            String cat = s.getCategoryName() != null ? s.getCategoryName() : "Unknown";
            categoryData.computeIfAbsent(cat, k -> new LinkedHashMap<>());
            categoryData.get(cat).merge(s.getSource() != null ? s.getSource() : "UNKNOWN", 1L, Long::sum);
        }

        // Build chart-friendly array sorted by total desc
        var categories = new ArrayList<Map<String, Object>>();
        categoryData.entrySet().stream()
                .sorted((a, b) -> Long.compare(
                        b.getValue().values().stream().mapToLong(Long::longValue).sum(),
                        a.getValue().values().stream().mapToLong(Long::longValue).sum()))
                .forEach(entry -> {
                    var c = new LinkedHashMap<String, Object>();
                    c.put("category", entry.getKey());
                    long autoCollected = entry.getValue().getOrDefault("AUTO_COLLECTED", 0L);
                    long manual = entry.getValue().getOrDefault("MANUAL_UPLOAD", 0L);
                    long correction = entry.getValue().getOrDefault("CORRECTION", 0L);
                    long bulkImport = entry.getValue().getOrDefault("BULK_IMPORT", 0L);
                    long total = autoCollected + manual + correction + bulkImport;
                    c.put("autoCollected", autoCollected);
                    c.put("manual", manual);
                    c.put("correction", correction);
                    c.put("bulkImport", bulkImport);
                    c.put("total", total);
                    c.put("graduated", total >= 5);
                    categories.add(c);
                });

        // Data growth over time (by createdAt)
        var growthMap = new LinkedHashMap<String, int[]>(); // [auto, manual, correction, bulk]
        for (var s : samples) {
            if (s.getCreatedAt() == null) continue;
            String day = s.getCreatedAt().toString().substring(0, 10);
            int[] counts = growthMap.computeIfAbsent(day, k -> new int[4]);
            switch (s.getSource() != null ? s.getSource() : "") {
                case "AUTO_COLLECTED" -> counts[0]++;
                case "MANUAL_UPLOAD" -> counts[1]++;
                case "CORRECTION" -> counts[2]++;
                case "BULK_IMPORT" -> counts[3]++;
            }
        }

        // Convert to cumulative
        var growth = new ArrayList<Map<String, Object>>();
        int cumAuto = 0, cumManual = 0, cumCorrection = 0, cumBulk = 0;
        for (var entry : growthMap.entrySet()) {
            cumAuto += entry.getValue()[0];
            cumManual += entry.getValue()[1];
            cumCorrection += entry.getValue()[2];
            cumBulk += entry.getValue()[3];
            var g = new LinkedHashMap<String, Object>();
            g.put("date", entry.getKey());
            g.put("autoCollected", cumAuto);
            g.put("manual", cumManual);
            g.put("correction", cumCorrection);
            g.put("bulkImport", cumBulk);
            g.put("total", cumAuto + cumManual + cumCorrection + cumBulk);
            growth.add(g);
        }

        var result = new LinkedHashMap<String, Object>();
        result.put("categories", categories);
        result.put("growth", growth);
        result.put("totalSamples", samples.size());
        result.put("graduationThreshold", 5);
        return ResponseEntity.ok(result);
    }

    /**
     * AI cost and token usage trends — daily aggregates of tokens, cost, latency.
     */
    @GetMapping("/ai-cost")
    public ResponseEntity<Map<String, Object>> aiCost(
            @RequestParam(defaultValue = "30") int days) {

        Instant cutoff = Instant.now().minus(days, ChronoUnit.DAYS);
        List<AiUsageLog> logs = aiUsageRepo.findByTimestampAfterOrderByTimestampAsc(cutoff);

        // Daily aggregates
        var dailyMap = new LinkedHashMap<String, double[]>(); // [inputTokens, outputTokens, cost, durationMs, count]
        for (var log : logs) {
            if (log.getTimestamp() == null) continue;
            String day = log.getTimestamp().toString().substring(0, 10);
            double[] agg = dailyMap.computeIfAbsent(day, k -> new double[5]);
            agg[0] += log.getInputTokens();
            agg[1] += log.getOutputTokens();
            agg[2] += log.getEstimatedCost();
            agg[3] += log.getDurationMs();
            agg[4]++;
        }

        var daily = new ArrayList<Map<String, Object>>();
        for (var entry : dailyMap.entrySet()) {
            double[] agg = entry.getValue();
            var d = new LinkedHashMap<String, Object>();
            d.put("date", entry.getKey());
            d.put("inputTokens", (long) agg[0]);
            d.put("outputTokens", (long) agg[1]);
            d.put("totalTokens", (long) (agg[0] + agg[1]));
            d.put("cost", Math.round(agg[2] * 10000) / 10000.0);
            d.put("avgLatencyMs", agg[4] > 0 ? Math.round(agg[3] / agg[4]) : 0);
            d.put("calls", (int) agg[4]);
            daily.add(d);
        }

        // Provider breakdown
        var byProvider = logs.stream()
                .filter(l -> l.getProvider() != null)
                .collect(Collectors.groupingBy(AiUsageLog::getProvider,
                        Collectors.collectingAndThen(Collectors.toList(), list -> {
                            var m = new LinkedHashMap<String, Object>();
                            m.put("calls", list.size());
                            m.put("totalTokens", list.stream().mapToLong(l -> l.getInputTokens() + l.getOutputTokens()).sum());
                            m.put("totalCost", Math.round(list.stream().mapToDouble(AiUsageLog::getEstimatedCost).sum() * 10000) / 10000.0);
                            m.put("avgLatencyMs", Math.round(list.stream().mapToLong(AiUsageLog::getDurationMs).average().orElse(0)));
                            return m;
                        })));

        // Model breakdown
        var byModel = logs.stream()
                .filter(l -> l.getModel() != null)
                .collect(Collectors.groupingBy(AiUsageLog::getModel,
                        Collectors.collectingAndThen(Collectors.toList(), list -> {
                            var m = new LinkedHashMap<String, Object>();
                            m.put("calls", list.size());
                            m.put("totalCost", Math.round(list.stream().mapToDouble(AiUsageLog::getEstimatedCost).sum() * 10000) / 10000.0);
                            return m;
                        })));

        var result = new LinkedHashMap<String, Object>();
        result.put("daily", daily);
        result.put("byProvider", byProvider);
        result.put("byModel", byModel);
        result.put("days", days);
        return ResponseEntity.ok(result);
    }

    /**
     * Correction/feedback analysis — breakdown by type, category, and trend over time.
     */
    @GetMapping("/corrections")
    public ResponseEntity<Map<String, Object>> corrections() {
        List<ClassificationCorrection> all = correctionRepo.findAll();

        // By type
        var byType = new LinkedHashMap<String, Long>();
        for (var c : all) {
            String type = c.getCorrectionType() != null ? c.getCorrectionType().name() : "UNKNOWN";
            byType.merge(type, 1L, Long::sum);
        }

        // By original category (which categories get overridden most)
        var byCategory = all.stream()
                .filter(c -> c.getOriginalCategoryName() != null)
                .collect(Collectors.groupingBy(ClassificationCorrection::getOriginalCategoryName, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(15)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (a, b) -> a, LinkedHashMap::new));

        // Override rate over time (daily)
        var dailyMap = new LinkedHashMap<String, int[]>(); // [corrections, approvals]
        for (var c : all) {
            if (c.getCorrectedAt() == null) continue;
            String day = c.getCorrectedAt().toString().substring(0, 10);
            int[] counts = dailyMap.computeIfAbsent(day, k -> new int[2]);
            if (c.getCorrectionType() == ClassificationCorrection.CorrectionType.APPROVED_CORRECT) {
                counts[1]++;
            } else {
                counts[0]++;
            }
        }

        var daily = new ArrayList<Map<String, Object>>();
        for (var entry : dailyMap.entrySet()) {
            var d = new LinkedHashMap<String, Object>();
            d.put("date", entry.getKey());
            d.put("corrections", entry.getValue()[0]);
            d.put("approvals", entry.getValue()[1]);
            int total = entry.getValue()[0] + entry.getValue()[1];
            d.put("total", total);
            d.put("overrideRate", total > 0 ? Math.round(entry.getValue()[0] * 1000.0 / total) / 10.0 : 0);
            daily.add(d);
        }

        // Confusion pairs: original → corrected category
        var confusionPairs = all.stream()
                .filter(c -> c.getOriginalCategoryName() != null && c.getCorrectedCategoryName() != null
                        && !c.getOriginalCategoryName().equals(c.getCorrectedCategoryName()))
                .collect(Collectors.groupingBy(
                        c -> c.getOriginalCategoryName() + " → " + c.getCorrectedCategoryName(),
                        Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(10)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (a, b) -> a, LinkedHashMap::new));

        var result = new LinkedHashMap<String, Object>();
        result.put("byType", byType);
        result.put("byCategory", byCategory);
        result.put("daily", daily);
        result.put("confusionPairs", confusionPairs);
        result.put("totalCorrections", all.size());
        return ResponseEntity.ok(result);
    }

    /**
     * Classification overview — category distribution, sensitivity distribution,
     * classifications per day.
     */
    @GetMapping("/classification-overview")
    public ResponseEntity<Map<String, Object>> classificationOverview(
            @RequestParam(defaultValue = "30") int days) {

        Instant cutoff = Instant.now().minus(days, ChronoUnit.DAYS);
        List<DocumentClassificationResult> results =
                classificationRepo.findByClassifiedAtAfterOrderByClassifiedAtAsc(cutoff);

        // By category
        var byCategory = results.stream()
                .filter(r -> r.getCategoryName() != null)
                .collect(Collectors.groupingBy(DocumentClassificationResult::getCategoryName, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(15)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (a, b) -> a, LinkedHashMap::new));

        // By sensitivity
        var bySensitivity = results.stream()
                .filter(r -> r.getSensitivityLabel() != null)
                .collect(Collectors.groupingBy(r -> r.getSensitivityLabel().name(), Collectors.counting()));

        // Daily volume
        var dailyMap = new LinkedHashMap<String, Integer>();
        for (var r : results) {
            if (r.getClassifiedAt() == null) continue;
            String day = r.getClassifiedAt().toString().substring(0, 10);
            dailyMap.merge(day, 1, Integer::sum);
        }

        var dailyVolume = new ArrayList<Map<String, Object>>();
        for (var entry : dailyMap.entrySet()) {
            dailyVolume.add(Map.of("date", entry.getKey(), "count", entry.getValue()));
        }

        var result = new LinkedHashMap<String, Object>();
        result.put("byCategory", byCategory);
        result.put("bySensitivity", bySensitivity);
        result.put("dailyVolume", dailyVolume);
        result.put("total", results.size());
        result.put("days", days);
        return ResponseEntity.ok(result);
    }

    // ── Scatter chart endpoints ────────────────────────────

    /**
     * Confidence vs Latency — each point is an AI classification call.
     * Reveals whether low-confidence classifications take longer (model struggling).
     */
    @GetMapping("/scatter/confidence-vs-latency")
    public ResponseEntity<Map<String, Object>> confidenceVsLatency(
            @RequestParam(defaultValue = "500") int limit) {

        List<AiUsageLog> logs = aiUsageRepo.findAll();

        var points = logs.stream()
                .filter(l -> "CLASSIFY".equals(l.getUsageType()) && "SUCCESS".equals(l.getStatus()))
                .filter(l -> l.getDurationMs() > 0)
                .sorted(Comparator.comparing(AiUsageLog::getTimestamp, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(limit)
                .map(l -> {
                    var p = new LinkedHashMap<String, Object>();
                    // Extract confidence from result map
                    double confidence = 0;
                    if (l.getResult() != null && l.getResult().get("confidence") != null) {
                        try { confidence = Double.parseDouble(l.getResult().get("confidence").toString()); }
                        catch (NumberFormatException ignored) {}
                    }
                    p.put("confidence", Math.round(confidence * 100) / 100.0);
                    p.put("latencyMs", l.getDurationMs());
                    p.put("provider", l.getProvider());
                    p.put("model", l.getModel());
                    p.put("tokens", l.getInputTokens() + l.getOutputTokens());
                    return p;
                })
                .filter(p -> (double) p.get("confidence") > 0)
                .toList();

        return ResponseEntity.ok(Map.of("points", points, "count", points.size()));
    }

    /**
     * Sample count vs per-class F1 — one point per category from the latest completed training job.
     * Shows whether more training data actually improves classification quality.
     */
    @GetMapping("/scatter/samples-vs-accuracy")
    public ResponseEntity<Map<String, Object>> samplesVsAccuracy() {
        // Get latest completed/promoted training job with per-class metrics
        List<BertTrainingJob> jobs = trainingJobRepo.findAllByOrderByStartedAtDesc();
        BertTrainingJob latest = jobs.stream()
                .filter(j -> j.getStatus() == BertTrainingJob.JobStatus.COMPLETED
                        || j.getStatus() == BertTrainingJob.JobStatus.PROMOTED)
                .filter(j -> j.getMetrics() != null && j.getMetrics().get("per_class") != null)
                .findFirst()
                .orElse(null);

        if (latest == null) {
            return ResponseEntity.ok(Map.of("points", List.of(), "count", 0, "version", "none"));
        }

        // Get current sample counts per category
        List<TrainingDataSample> samples = trainingDataRepo.findAll();
        var sampleCounts = samples.stream()
                .filter(s -> s.getCategoryName() != null)
                .collect(Collectors.groupingBy(TrainingDataSample::getCategoryName, Collectors.counting()));

        @SuppressWarnings("unchecked")
        Map<String, Object> perClass = (Map<String, Object>) latest.getMetrics().get("per_class");

        var points = new ArrayList<Map<String, Object>>();
        for (var entry : perClass.entrySet()) {
            String category = entry.getKey();
            @SuppressWarnings("unchecked")
            Map<String, Object> metrics = (Map<String, Object>) entry.getValue();

            var p = new LinkedHashMap<String, Object>();
            p.put("category", category);
            p.put("samples", sampleCounts.getOrDefault(category, 0L));

            double f1 = 0;
            if (metrics.get("f1") != null) {
                try { f1 = Double.parseDouble(metrics.get("f1").toString()); }
                catch (NumberFormatException ignored) {}
            }
            p.put("f1", Math.round(f1 * 1000) / 1000.0);

            double precision = 0;
            if (metrics.get("precision") != null) {
                try { precision = Double.parseDouble(metrics.get("precision").toString()); }
                catch (NumberFormatException ignored) {}
            }
            p.put("precision", Math.round(precision * 1000) / 1000.0);

            double recall = 0;
            if (metrics.get("recall") != null) {
                try { recall = Double.parseDouble(metrics.get("recall").toString()); }
                catch (NumberFormatException ignored) {}
            }
            p.put("recall", Math.round(recall * 1000) / 1000.0);

            int support = 0;
            if (metrics.get("support") != null) {
                try { support = Integer.parseInt(metrics.get("support").toString()); }
                catch (NumberFormatException ignored) {}
            }
            p.put("support", support);

            points.add(p);
        }

        return ResponseEntity.ok(Map.of(
                "points", points,
                "count", points.size(),
                "version", latest.getModelVersion()
        ));
    }

    /**
     * Token count vs Cost — each point is an AI call.
     * Spot outlier classifications that consumed disproportionate resources.
     */
    @GetMapping("/scatter/tokens-vs-cost")
    public ResponseEntity<Map<String, Object>> tokensVsCost(
            @RequestParam(defaultValue = "500") int limit) {

        List<AiUsageLog> logs = aiUsageRepo.findAll();

        var points = logs.stream()
                .filter(l -> l.getInputTokens() + l.getOutputTokens() > 0)
                .sorted(Comparator.comparing(AiUsageLog::getTimestamp, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(limit)
                .map(l -> {
                    var p = new LinkedHashMap<String, Object>();
                    p.put("totalTokens", l.getInputTokens() + l.getOutputTokens());
                    p.put("inputTokens", l.getInputTokens());
                    p.put("outputTokens", l.getOutputTokens());
                    p.put("cost", Math.round(l.getEstimatedCost() * 100000) / 100000.0);
                    p.put("provider", l.getProvider());
                    p.put("model", l.getModel());
                    p.put("usageType", l.getUsageType());
                    p.put("latencyMs", l.getDurationMs());
                    return p;
                })
                .toList();

        return ResponseEntity.ok(Map.of("points", points, "count", points.size()));
    }

    /**
     * Document size vs Confidence — each point is a classified document.
     * Reveals whether longer/shorter documents are harder to classify.
     */
    @GetMapping("/scatter/size-vs-confidence")
    public ResponseEntity<Map<String, Object>> sizeVsConfidence(
            @RequestParam(defaultValue = "500") int limit) {

        List<DocumentClassificationResult> results = classificationRepo.findAll();

        // Group by documentId, take latest classification per doc
        var latestByDoc = new LinkedHashMap<String, DocumentClassificationResult>();
        for (var r : results) {
            if (r.getDocumentId() == null || r.getConfidence() <= 0) continue;
            latestByDoc.merge(r.getDocumentId(), r, (existing, newer) ->
                    newer.getClassifiedAt() != null && (existing.getClassifiedAt() == null
                            || newer.getClassifiedAt().isAfter(existing.getClassifiedAt())) ? newer : existing);
        }

        var points = new ArrayList<Map<String, Object>>();
        int count = 0;
        for (var entry : latestByDoc.entrySet()) {
            if (count >= limit) break;
            DocumentModel doc = documentService.getById(entry.getKey());
            if (doc == null) continue;

            long textLength = doc.getExtractedText() != null ? doc.getExtractedText().length() : 0;
            if (textLength == 0) continue;

            var cr = entry.getValue();
            boolean isBert = cr.getTags() != null && cr.getTags().contains("bert-classified");

            var p = new LinkedHashMap<String, Object>();
            p.put("textLength", textLength);
            p.put("fileSizeBytes", doc.getFileSizeBytes());
            p.put("confidence", Math.round(cr.getConfidence() * 1000) / 1000.0);
            p.put("category", cr.getCategoryName());
            p.put("classifier", isBert ? "BERT" : "LLM");
            p.put("sensitivity", cr.getSensitivityLabel() != null ? cr.getSensitivityLabel().name() : null);
            points.add(p);
            count++;
        }

        return ResponseEntity.ok(Map.of("points", points, "count", points.size()));
    }
}
