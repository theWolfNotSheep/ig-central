package co.uk.wolfnotsheep.infrastructure.controllers.admin;

import co.uk.wolfnotsheep.document.models.DocumentModel;
import co.uk.wolfnotsheep.document.models.DocumentStatus;
import co.uk.wolfnotsheep.document.services.DocumentService;
import co.uk.wolfnotsheep.governance.models.DocumentClassificationResult;
import co.uk.wolfnotsheep.governance.models.TrainingDataSample;
import co.uk.wolfnotsheep.governance.repositories.DocumentClassificationResultRepository;
import co.uk.wolfnotsheep.governance.repositories.TrainingDataSampleRepository;
import co.uk.wolfnotsheep.infrastructure.services.BertRetrainingAdvisor;
import co.uk.wolfnotsheep.platform.config.services.AppConfigService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Admin endpoints for BERT model management:
 * - Health/status of the BERT sidecar
 * - Training data export from classified documents
 * - Model listing
 * - Hit/miss statistics
 */
@RestController
@RequestMapping("/api/admin/bert")
public class BertModelController {

    private static final Logger log = LoggerFactory.getLogger(BertModelController.class);

    private final DocumentService documentService;
    private final DocumentClassificationResultRepository classificationRepo;
    private final TrainingDataSampleRepository trainingDataSampleRepo;
    private final BertRetrainingAdvisor retrainingAdvisor;
    private final AppConfigService configService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String defaultServiceUrl;

    public BertModelController(DocumentService documentService,
                                DocumentClassificationResultRepository classificationRepo,
                                TrainingDataSampleRepository trainingDataSampleRepo,
                                BertRetrainingAdvisor retrainingAdvisor,
                                AppConfigService configService,
                                @Value("${pipeline.bert.service-url:http://bert-classifier:8000}") String defaultServiceUrl) {
        this.documentService = documentService;
        this.classificationRepo = classificationRepo;
        this.trainingDataSampleRepo = trainingDataSampleRepo;
        this.retrainingAdvisor = retrainingAdvisor;
        this.configService = configService;
        this.objectMapper = new ObjectMapper();
        this.defaultServiceUrl = defaultServiceUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    /**
     * Get BERT service status: health, mode, model info, label count.
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        String serviceUrl = configService.getValue("pipeline.bert.service-url", defaultServiceUrl);
        var result = new LinkedHashMap<String, Object>();
        result.put("serviceUrl", serviceUrl);

        try {
            var req = HttpRequest.newBuilder()
                    .uri(URI.create(serviceUrl + "/health"))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            var resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() == 200) {
                JsonNode json = objectMapper.readTree(resp.body());
                result.put("reachable", true);
                result.put("status", json.path("status").asText("unknown"));
                result.put("modelLoaded", json.path("model_loaded").asBoolean(false));
                result.put("mode", json.path("mode").asText("unknown"));
                result.put("labelCount", json.path("label_count").asInt(0));
            } else {
                result.put("reachable", true);
                result.put("status", "error");
                result.put("statusCode", resp.statusCode());
            }
        } catch (Exception e) {
            result.put("reachable", false);
            result.put("status", "unreachable");
            result.put("error", e.getMessage());
        }

        // List available models
        try {
            var req = HttpRequest.newBuilder()
                    .uri(URI.create(serviceUrl + "/models"))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            var resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                JsonNode json = objectMapper.readTree(resp.body());
                result.put("models", objectMapper.treeToValue(json.path("models"), List.class));
                result.put("activeModelDir", json.path("active_model_dir").asText());
            }
        } catch (Exception ignored) {}

        return ResponseEntity.ok(result);
    }

    /**
     * Export training data from classified documents.
     * Returns JSON lines format: {text, category_id, category_name, sensitivity_label, confidence}
     */
    @GetMapping("/training-data")
    public ResponseEntity<Map<String, Object>> trainingData(
            @RequestParam(defaultValue = "0.7") double minConfidence,
            @RequestParam(defaultValue = "1000") int limit) {

        List<DocumentClassificationResult> results = classificationRepo.findAll();

        // Filter to high-confidence, human-approved, or corrected classifications
        var trainingPairs = new ArrayList<Map<String, Object>>();
        var categoryCounts = new LinkedHashMap<String, Integer>();

        for (var cr : results) {
            if (cr.getConfidence() < minConfidence) continue;
            if (cr.getCategoryName() == null) continue;

            // Get the document's extracted text
            DocumentModel doc = documentService.getById(cr.getDocumentId());
            if (doc == null || doc.getExtractedText() == null || doc.getExtractedText().isBlank()) continue;

            // Truncate text for training (BERT max is typically 512 tokens ~ 2000 chars)
            String text = doc.getExtractedText();
            if (text.length() > 2000) text = text.substring(0, 2000);

            var pair = new LinkedHashMap<String, Object>();
            pair.put("text", text);
            pair.put("category_id", cr.getCategoryId());
            pair.put("category_name", cr.getCategoryName());
            pair.put("sensitivity_label", cr.getSensitivityLabel() != null ? cr.getSensitivityLabel().name() : "INTERNAL");
            pair.put("confidence", cr.getConfidence());
            pair.put("document_id", cr.getDocumentId());
            pair.put("corrected", false);

            trainingPairs.add(pair);
            categoryCounts.merge(cr.getCategoryName(), 1, Integer::sum);

            if (trainingPairs.size() >= limit) break;
        }

        var result = new LinkedHashMap<String, Object>();
        result.put("totalSamples", trainingPairs.size());
        result.put("categories", categoryCounts);
        result.put("categoryCount", categoryCounts.size());
        result.put("minConfidence", minConfidence);
        result.put("samples", trainingPairs);

        return ResponseEntity.ok(result);
    }

    /**
     * Generate a label_map.json from current taxonomy categories that have classified documents.
     */
    @GetMapping("/label-map")
    public ResponseEntity<Map<String, Object>> generateLabelMap() {
        List<DocumentClassificationResult> results = classificationRepo.findAll();

        // Collect unique categories
        var categories = new LinkedHashMap<String, Map<String, String>>();
        for (var cr : results) {
            if (cr.getCategoryId() == null || cr.getCategoryName() == null) continue;
            categories.putIfAbsent(cr.getCategoryId(), Map.of(
                    "category_id", cr.getCategoryId(),
                    "category_name", cr.getCategoryName(),
                    "sensitivity_label", cr.getSensitivityLabel() != null ? cr.getSensitivityLabel().name() : "INTERNAL"
            ));
        }

        // Build indexed label map
        var labelMap = new LinkedHashMap<String, Map<String, String>>();
        int idx = 0;
        for (var entry : categories.values()) {
            labelMap.put(String.valueOf(idx++), entry);
        }

        var result = new LinkedHashMap<String, Object>();
        result.put("labelCount", labelMap.size());
        result.put("labelMap", labelMap);

        return ResponseEntity.ok(result);
    }

    /**
     * BERT pipeline statistics: how often BERT hit vs missed, average confidence.
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> stats() {
        List<DocumentClassificationResult> results = classificationRepo.findAll();

        long bertHits = results.stream()
                .filter(r -> r.getTags() != null && r.getTags().contains("bert-classified"))
                .count();
        long llmClassified = results.stream()
                .filter(r -> r.getModelId() != null && !r.getModelId().isEmpty())
                .filter(r -> r.getTags() == null || !r.getTags().contains("bert-classified"))
                .count();
        long total = bertHits + llmClassified;

        double avgBertConfidence = results.stream()
                .filter(r -> r.getTags() != null && r.getTags().contains("bert-classified"))
                .mapToDouble(DocumentClassificationResult::getConfidence)
                .average()
                .orElse(0);

        var result = new LinkedHashMap<String, Object>();
        result.put("totalClassified", total);
        result.put("bertHits", bertHits);
        result.put("llmClassified", llmClassified);
        result.put("bertHitRate", total > 0 ? String.format("%.1f%%", (bertHits * 100.0 / total)) : "0%");
        result.put("avgBertConfidence", Math.round(avgBertConfidence * 100) / 100.0);

        return ResponseEntity.ok(result);
    }

    /**
     * Training readiness: how much data exists, category distribution, recommendation.
     */
    @GetMapping("/training-readiness")
    public ResponseEntity<Map<String, Object>> trainingReadiness() {
        var samples = trainingDataSampleRepo.findAll();

        long total = samples.size();
        long verified = samples.stream().filter(TrainingDataSample::isVerified).count();
        long corrections = samples.stream().filter(s -> "CORRECTION".equals(s.getSource())).count();
        long autoCollected = samples.stream().filter(s -> "AUTO_COLLECTED".equals(s.getSource())).count();

        var categoryDist = new LinkedHashMap<String, Long>();
        samples.stream()
                .filter(s -> s.getCategoryName() != null)
                .collect(Collectors.groupingBy(TrainingDataSample::getCategoryName, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .forEach(e -> categoryDist.put(e.getKey(), e.getValue()));

        var ready = new ArrayList<String>();
        var insufficient = new ArrayList<String>();
        categoryDist.forEach((cat, count) -> {
            if (count >= 10) ready.add(cat + " (" + count + ")");
            else insufficient.add(cat + " (" + count + ")");
        });

        String recommendation;
        if (total < 20) recommendation = "NOT_ENOUGH_DATA";
        else if (ready.isEmpty()) recommendation = "INSUFFICIENT_PER_CATEGORY";
        else if (!insufficient.isEmpty()) recommendation = "PARTIAL_READY";
        else recommendation = "READY";

        var result = new LinkedHashMap<String, Object>();
        result.put("totalSamples", total);
        result.put("verified", verified);
        result.put("corrections", corrections);
        result.put("autoCollected", autoCollected);
        result.put("categoryCount", categoryDist.size());
        result.put("categoryDistribution", categoryDist);
        result.put("readyCategories", ready);
        result.put("insufficientCategories", insufficient);
        result.put("recommendation", recommendation);

        return ResponseEntity.ok(result);
    }

    /**
     * Retraining advisor: should we retrain? Based on new samples, corrections, model age.
     */
    @GetMapping("/retraining-advice")
    public ResponseEntity<Map<String, Object>> retrainingAdvice() {
        return ResponseEntity.ok(retrainingAdvisor.getLastAdvice());
    }
}
