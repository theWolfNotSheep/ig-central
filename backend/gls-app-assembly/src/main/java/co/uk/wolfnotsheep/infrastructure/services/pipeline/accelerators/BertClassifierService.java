package co.uk.wolfnotsheep.infrastructure.services.pipeline.accelerators;

import co.uk.wolfnotsheep.document.models.DocumentModel;
import co.uk.wolfnotsheep.governance.models.SensitivityLabel;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Accelerator: calls an external BERT classifier service via HTTP.
 * The BERT service runs as a Python sidecar (FastAPI + ONNX Runtime)
 * and returns category predictions with confidence scores.
 *
 * If the BERT model's confidence exceeds the configured threshold,
 * the LLM classification step is skipped entirely.
 */
@Service
public class BertClassifierService implements AcceleratorHandler {

    @Override
    public String getNodeTypeKey() { return "bertClassifier"; }

    private static final Logger log = LoggerFactory.getLogger(BertClassifierService.class);

    private static final int DEFAULT_MAX_TOKENS = 512;
    private static final double DEFAULT_THRESHOLD = 0.85;
    private static final int DEFAULT_TIMEOUT_MS = 5000;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String defaultServiceUrl;

    public BertClassifierService(
            @Value("${pipeline.bert.service-url:http://bert-classifier:8000}") String defaultServiceUrl) {
        this.objectMapper = new ObjectMapper();
        this.defaultServiceUrl = defaultServiceUrl;
        // Force HTTP/1.1 — Uvicorn doesn't support HTTP/2 upgrade
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    /**
     * Classify a document using the external BERT service.
     *
     * Node config keys:
     * - serviceUrl: override the default BERT service URL
     * - confidenceThreshold: minimum confidence to accept (default 0.85)
     * - maxTokens: max tokens to send to BERT (default 512)
     * - modelId: which model to use (passed to the service)
     * - timeoutMs: HTTP request timeout (default 5000)
     */
    public AcceleratorResult evaluate(DocumentModel doc, Map<String, Object> nodeConfig) {
        String text = doc.getExtractedText();
        if (text == null || text.isBlank()) {
            return AcceleratorResult.miss();
        }

        String serviceUrl = toString(nodeConfig.get("serviceUrl"), defaultServiceUrl);
        double threshold = toDouble(nodeConfig.get("confidenceThreshold"), DEFAULT_THRESHOLD);
        int maxTokens = toInt(nodeConfig.get("maxTokens"), DEFAULT_MAX_TOKENS);
        String modelId = toString(nodeConfig.get("modelId"), null);
        int timeoutMs = toInt(nodeConfig.get("timeoutMs"), DEFAULT_TIMEOUT_MS);

        // Truncate text to maxTokens worth of words (rough approximation)
        String truncatedText = truncateToTokens(text, maxTokens);

        try {
            // Build request payload
            var payload = new java.util.HashMap<String, Object>();
            payload.put("text", truncatedText);
            payload.put("document_id", doc.getId());
            if (doc.getMimeType() != null) {
                payload.put("mime_type", doc.getMimeType());
            }
            if (modelId != null) {
                payload.put("model_id", modelId);
            }

            String requestBody = objectMapper.writeValueAsString(payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(serviceUrl + "/classify"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofMillis(timeoutMs))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("[BERT] Service returned status {} for doc {}", response.statusCode(), doc.getId());
                return AcceleratorResult.miss();
            }

            JsonNode json = objectMapper.readTree(response.body());

            double confidence = json.path("confidence").asDouble(0);
            String categoryId = json.path("category_id").asText(null);
            String categoryName = json.path("category_name").asText(null);
            String sensitivityStr = json.path("sensitivity_label").asText(null);
            String reasoning = json.path("reasoning").asText("BERT classification");

            // Parse tags
            List<String> tags = new ArrayList<>();
            if (json.has("tags") && json.get("tags").isArray()) {
                for (JsonNode tag : json.get("tags")) {
                    tags.add(tag.asText());
                }
            }

            // _OTHER prediction means BERT doesn't recognise this document type — pass to LLM
            if ("_OTHER".equals(categoryId) || "_Other".equals(categoryName)) {
                log.info("[BERT] Predicted _OTHER for doc {} (confidence: {}) — passing to LLM",
                        doc.getId(), String.format("%.3f", confidence));
                return AcceleratorResult.miss();
            }

            if (categoryId == null || confidence < threshold) {
                log.info("[BERT] Below threshold for doc {} — confidence: {}, threshold: {}",
                        doc.getId(), String.format("%.3f", confidence), threshold);
                return AcceleratorResult.miss();
            }

            SensitivityLabel sensitivity = parseSensitivity(sensitivityStr);

            log.info("[BERT] Classification for doc {} — category: {}, confidence: {}",
                    doc.getId(), categoryName, String.format("%.3f", confidence));

            return AcceleratorResult.hit(
                    categoryId, categoryName,
                    sensitivity, tags,
                    null, // retentionScheduleId — BERT doesn't determine retention
                    confidence, reasoning, "bertClassifier"
            );

        } catch (java.net.ConnectException e) {
            log.warn("[BERT] Service unavailable at {} — skipping for doc {}", serviceUrl, doc.getId());
            return AcceleratorResult.miss();
        } catch (java.net.http.HttpTimeoutException e) {
            log.warn("[BERT] Service timed out ({}ms) for doc {}", timeoutMs, doc.getId());
            return AcceleratorResult.miss();
        } catch (Exception e) {
            log.error("[BERT] Error classifying doc {}: {}", doc.getId(), e.getMessage(), e);
            return AcceleratorResult.miss();
        }
    }

    private SensitivityLabel parseSensitivity(String label) {
        if (label == null) return SensitivityLabel.INTERNAL;
        try {
            return SensitivityLabel.valueOf(label.toUpperCase());
        } catch (IllegalArgumentException e) {
            return SensitivityLabel.INTERNAL;
        }
    }

    /**
     * Rough token truncation — splits on whitespace and takes first N words.
     * BERT tokenizers typically produce ~1.3 tokens per word, so we use
     * maxTokens * 0.75 as the word limit.
     */
    private String truncateToTokens(String text, int maxTokens) {
        int maxWords = (int) (maxTokens * 0.75);
        String[] words = text.split("\\s+");
        if (words.length <= maxWords) return text;

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < maxWords; i++) {
            if (i > 0) sb.append(" ");
            sb.append(words[i]);
        }
        return sb.toString();
    }

    private static String toString(Object val, String defaultVal) {
        if (val instanceof String s && !s.isBlank()) return s;
        return defaultVal;
    }

    private static double toDouble(Object val, double defaultVal) {
        if (val instanceof Number n) return n.doubleValue();
        if (val instanceof String s) { try { return Double.parseDouble(s); } catch (Exception e) { return defaultVal; } }
        return defaultVal;
    }

    private static int toInt(Object val, int defaultVal) {
        if (val instanceof Number n) return n.intValue();
        if (val instanceof String s) { try { return Integer.parseInt(s); } catch (Exception e) { return defaultVal; } }
        return defaultVal;
    }
}
