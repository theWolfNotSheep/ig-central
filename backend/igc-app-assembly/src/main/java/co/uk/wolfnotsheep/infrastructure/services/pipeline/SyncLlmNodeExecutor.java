package co.uk.wolfnotsheep.infrastructure.services.pipeline;

import co.uk.wolfnotsheep.document.events.DocumentClassifiedEvent;
import co.uk.wolfnotsheep.document.events.DocumentIngestedEvent;
import co.uk.wolfnotsheep.document.models.DocumentModel;
import co.uk.wolfnotsheep.governance.models.PipelineBlock;
import co.uk.wolfnotsheep.governance.repositories.PipelineBlockRepository;
import co.uk.wolfnotsheep.platform.config.services.AppConfigService;
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
import java.time.Instant;
import java.util.*;

/**
 * Executes LLM nodes synchronously by calling the llm-worker's REST API.
 * Supports two modes:
 * - CLASSIFICATION: full MCP-based classification (save_classification_result tool)
 * - CUSTOM_PROMPT: arbitrary LLM instruction returning structured data
 *
 * This keeps all LLM infrastructure (Spring AI, Anthropic, MCP) in igc-llm-orchestration
 * while allowing igc-app-assembly's execution engine to call it synchronously via HTTP.
 */
@Service
public class SyncLlmNodeExecutor {

    private static final Logger log = LoggerFactory.getLogger(SyncLlmNodeExecutor.class);

    private final PipelineBlockRepository blockRepo;
    private final AppConfigService configService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String defaultServiceUrl;
    private final int defaultTimeoutMs;

    public SyncLlmNodeExecutor(PipelineBlockRepository blockRepo,
                                AppConfigService configService,
                                @Value("${pipeline.llm.service-url:http://llm-worker:8082}") String defaultServiceUrl,
                                @Value("${pipeline.llm.timeout-ms:120000}") int defaultTimeoutMs) {
        this.blockRepo = blockRepo;
        this.configService = configService;
        this.objectMapper = new ObjectMapper();
        this.defaultServiceUrl = defaultServiceUrl;
        this.defaultTimeoutMs = defaultTimeoutMs;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Result of a synchronous LLM call.
     */
    public record SyncLlmResult(
            boolean success,
            DocumentClassifiedEvent classificationEvent,  // for CLASSIFICATION mode
            Map<String, Object> customResult,             // for CUSTOM_PROMPT mode
            String error
    ) {
        public static SyncLlmResult classification(DocumentClassifiedEvent event) {
            return new SyncLlmResult(true, event, null, null);
        }
        public static SyncLlmResult custom(Map<String, Object> result) {
            return new SyncLlmResult(true, null, result, null);
        }
        public static SyncLlmResult error(String message) {
            return new SyncLlmResult(false, null, null, message);
        }
    }

    /**
     * Execute an LLM node synchronously.
     * Determines mode from the linked block content and calls the LLM worker REST API.
     */
    public SyncLlmResult execute(DocumentModel doc, Map<String, Object> nodeConfig,
                                  String nodeTypeKey, DocumentIngestedEvent ingestedEvent) {
        String blockId = nodeConfig.get("blockId") != null ? nodeConfig.get("blockId").toString() : null;

        // Determine mode: check if the block is a standard classification prompt or custom
        String mode = determineMode(blockId, nodeConfig);

        String serviceUrl = configService.getValue("pipeline.llm.service-url", defaultServiceUrl);
        int timeoutMs = toInt(nodeConfig.get("timeoutMs"), defaultTimeoutMs);

        try {
            // Build request payload
            var payload = new LinkedHashMap<String, Object>();
            payload.put("documentId", doc.getId());
            payload.put("fileName", doc.getOriginalFileName());
            payload.put("mimeType", doc.getMimeType());
            payload.put("fileSizeBytes", doc.getFileSizeBytes());
            payload.put("extractedText", doc.getExtractedText());
            payload.put("storageLocation", doc.getStorageBucket() + "/" + doc.getStorageKey());
            payload.put("uploadedBy", ingestedEvent != null ? ingestedEvent.uploadedBy() : "SYSTEM");
            payload.put("pipelineId", doc.getPipelineId());
            payload.put("blockId", blockId);
            payload.put("mode", mode);

            // Per-node LLM overrides from the visual editor (provider, model, temperature,
            // maxTokens, timeoutSeconds, prompt-injection toggles).
            if (nodeConfig.get("provider") != null && !nodeConfig.get("provider").toString().isBlank()) {
                payload.put("provider", nodeConfig.get("provider").toString());
            }
            if (nodeConfig.get("model") != null && !nodeConfig.get("model").toString().isBlank()) {
                payload.put("model", nodeConfig.get("model").toString());
            }
            if (nodeConfig.get("temperature") != null) {
                payload.put("temperature", toDouble(nodeConfig.get("temperature")));
            }
            if (nodeConfig.get("maxTokens") != null) {
                payload.put("maxTokens", toInt(nodeConfig.get("maxTokens"), 0));
            }
            if (nodeConfig.get("timeoutSeconds") != null) {
                payload.put("timeoutSeconds", toInt(nodeConfig.get("timeoutSeconds"), 0));
            }
            // Prompt-injection toggles — pre-load taxonomy/sensitivities/etc. to skip MCP round-trips
            for (String key : new String[]{"injectTaxonomy", "injectSensitivities", "injectTraits", "injectPiiTypes"}) {
                if (nodeConfig.get(key) != null) {
                    payload.put(key, Boolean.parseBoolean(nodeConfig.get(key).toString()));
                }
            }

            String requestBody = objectMapper.writeValueAsString(payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(serviceUrl + "/api/internal/classify"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofMillis(timeoutMs))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            log.info("[SyncLLM] Calling {} for doc {} mode={}", serviceUrl, doc.getId(), mode);
            long start = System.currentTimeMillis();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            long durationMs = System.currentTimeMillis() - start;
            log.info("[SyncLLM] Response received for doc {} in {}ms — status {}", doc.getId(), durationMs, response.statusCode());

            if (response.statusCode() != 200) {
                log.error("[SyncLLM] LLM service returned status {} for doc {}: {}", response.statusCode(), doc.getId(), response.body());
                return SyncLlmResult.error("LLM service returned " + response.statusCode());
            }

            JsonNode json = objectMapper.readTree(response.body());

            if (!json.path("success").asBoolean(false)) {
                String error = json.path("error").asText("Unknown error");
                log.error("[SyncLLM] LLM service returned error for doc {}: {}", doc.getId(), error);
                return SyncLlmResult.error(error);
            }

            if ("CLASSIFICATION".equals(mode)) {
                return buildClassificationResult(json, doc);
            } else {
                return buildCustomResult(json);
            }

        } catch (java.net.ConnectException e) {
            log.error("[SyncLLM] LLM service unavailable at {}: {}", serviceUrl, e.getMessage());
            return SyncLlmResult.error("LLM service unavailable: " + e.getMessage());
        } catch (java.net.http.HttpTimeoutException e) {
            log.error("[SyncLLM] LLM service timed out ({}ms) for doc {}", timeoutMs, doc.getId());
            return SyncLlmResult.error("LLM service timed out after " + timeoutMs + "ms");
        } catch (Exception e) {
            log.error("[SyncLLM] Error calling LLM for doc {}: {}", doc.getId(), e.getMessage(), e);
            return SyncLlmResult.error(e.getMessage());
        }
    }

    private String determineMode(String blockId, Map<String, Object> nodeConfig) {
        // Explicit mode override from node config
        Object modeOverride = nodeConfig.get("llmMode");
        if (modeOverride != null) return modeOverride.toString();

        if (blockId == null) return "CLASSIFICATION"; // default

        // Check block content to infer mode
        return blockRepo.findById(blockId).map(block -> {
            Map<String, Object> content = block.getActiveContent();
            if (content == null) return "CLASSIFICATION";

            // If the block has a standard classification system prompt (mentions taxonomy/MCP tools),
            // it's a classification prompt. Otherwise, treat as custom.
            Object systemPrompt = content.get("systemPrompt");
            if (systemPrompt != null) {
                String sp = systemPrompt.toString().toLowerCase();
                if (sp.contains("save_classification_result") || sp.contains("classification taxonomy")
                        || sp.contains("get_classification_taxonomy")) {
                    return "CLASSIFICATION";
                }
            }
            return "CUSTOM_PROMPT";
        }).orElse("CLASSIFICATION");
    }

    private SyncLlmResult buildClassificationResult(JsonNode json, DocumentModel doc) {
        String categoryId = json.path("categoryId").asText(null);
        String categoryName = json.path("categoryName").asText(null);
        String sensitivityStr = json.path("sensitivityLabel").asText("INTERNAL");
        double confidence = json.path("confidence").asDouble(0);
        boolean needsReview = json.path("needsReview").asBoolean(false);
        String retentionScheduleId = json.path("retentionScheduleId").asText(null);
        String resultId = json.path("classificationResultId").asText(null);

        List<String> tags = new ArrayList<>();
        if (json.has("tags") && json.get("tags").isArray()) {
            for (JsonNode t : json.get("tags")) tags.add(t.asText());
        }

        List<String> policyIds = new ArrayList<>();
        if (json.has("applicablePolicyIds") && json.get("applicablePolicyIds").isArray()) {
            for (JsonNode p : json.get("applicablePolicyIds")) policyIds.add(p.asText());
        }

        co.uk.wolfnotsheep.governance.models.SensitivityLabel sensitivity;
        try { sensitivity = co.uk.wolfnotsheep.governance.models.SensitivityLabel.valueOf(sensitivityStr.toUpperCase()); }
        catch (Exception e) { sensitivity = co.uk.wolfnotsheep.governance.models.SensitivityLabel.INTERNAL; }

        var event = new DocumentClassifiedEvent(
                doc.getId(), resultId, categoryId, categoryName,
                sensitivity, tags, policyIds, retentionScheduleId,
                confidence, needsReview, Instant.now()
        );

        return SyncLlmResult.classification(event);
    }

    @SuppressWarnings("unchecked")
    private SyncLlmResult buildCustomResult(JsonNode json) {
        Map<String, Object> result = new LinkedHashMap<>();

        // Get parsed result if available
        JsonNode parsedNode = json.path("parsedResult");
        if (!parsedNode.isMissingNode() && !parsedNode.isNull()) {
            try {
                result = objectMapper.convertValue(parsedNode, LinkedHashMap.class);
            } catch (Exception ignored) {}
        }

        // Include raw LLM response
        String llmResponse = json.path("llmResponse").asText(null);
        if (llmResponse != null && result.isEmpty()) {
            result.put("llmResponse", llmResponse);
        }

        return SyncLlmResult.custom(result);
    }

    private static int toInt(Object val, int defaultVal) {
        if (val instanceof Number n) return n.intValue();
        if (val instanceof String s) { try { return Integer.parseInt(s); } catch (Exception e) { return defaultVal; } }
        return defaultVal;
    }

    private static Double toDouble(Object val) {
        if (val instanceof Number n) return n.doubleValue();
        if (val instanceof String s) { try { return Double.parseDouble(s); } catch (Exception e) { return null; } }
        return null;
    }
}
