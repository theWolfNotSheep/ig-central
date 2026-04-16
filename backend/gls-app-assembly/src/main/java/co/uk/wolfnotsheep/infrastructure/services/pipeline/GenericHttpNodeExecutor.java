package co.uk.wolfnotsheep.infrastructure.services.pipeline;

import co.uk.wolfnotsheep.document.models.DocumentModel;
import co.uk.wolfnotsheep.governance.models.NodeTypeDefinition;
import co.uk.wolfnotsheep.governance.models.NodeTypeDefinition.GenericHttpConfig;
import co.uk.wolfnotsheep.governance.models.SensitivityLabel;
import co.uk.wolfnotsheep.governance.services.NodeTypeDefinitionService;
import co.uk.wolfnotsheep.infrastructure.services.pipeline.accelerators.AcceleratorResult;
import co.uk.wolfnotsheep.platform.config.services.AppConfigService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

/**
 * Generic HTTP node executor for the GENERIC_HTTP execution category.
 * Reads URL, body template, and response mapping from the NodeTypeDefinition's
 * httpConfig and executes an HTTP call against an external service.
 *
 * This enables adding new HTTP-based classification services (like BERT)
 * without writing any Java code — just create a NodeTypeDefinition with
 * executionCategory=GENERIC_HTTP and appropriate httpConfig.
 *
 * Security: URLs are validated against an allowlist configured via
 * AppConfigService key "pipeline.http.allowed-hosts".
 */
@Service
public class GenericHttpNodeExecutor {

    private static final Logger log = LoggerFactory.getLogger(GenericHttpNodeExecutor.class);
    private static final List<String> DEFAULT_ALLOWED_HOSTS = List.of(
            "bert-classifier", "localhost", "127.0.0.1"
    );

    private final NodeTypeDefinitionService nodeTypeService;
    private final AppConfigService configService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public GenericHttpNodeExecutor(NodeTypeDefinitionService nodeTypeService,
                                    AppConfigService configService) {
        this.nodeTypeService = nodeTypeService;
        this.configService = configService;
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    /**
     * Execute a GENERIC_HTTP node for the given document.
     * Reads httpConfig from the NodeTypeDefinition and merges with runtime nodeConfig overrides.
     */
    public AcceleratorResult execute(DocumentModel doc, Map<String, Object> nodeConfig, String nodeTypeKey) {
        NodeTypeDefinition typeDef = nodeTypeService.getByKey(nodeTypeKey).orElse(null);
        if (typeDef == null || typeDef.getHttpConfig() == null) {
            log.warn("[GenericHTTP] No httpConfig found for node type: {}", nodeTypeKey);
            return AcceleratorResult.miss();
        }

        GenericHttpConfig httpConfig = typeDef.getHttpConfig();

        // Resolve URL: runtime override > httpConfig default
        String baseUrl = toString(nodeConfig.get("serviceUrl"),
                httpConfig.defaultUrl() != null ? httpConfig.defaultUrl() : "http://localhost:8000");
        String path = toString(nodeConfig.get("path"),
                httpConfig.path() != null ? httpConfig.path() : "/classify");
        String fullUrl = baseUrl + path;

        // Security: validate host against allowlist
        if (!isHostAllowed(fullUrl)) {
            log.warn("[GenericHTTP] Host not in allowlist for URL: {}", fullUrl);
            return AcceleratorResult.miss();
        }

        int timeoutMs = toInt(nodeConfig.get("timeoutMs"), httpConfig.defaultTimeoutMs() > 0 ? httpConfig.defaultTimeoutMs() : 5000);
        String method = toString(nodeConfig.get("method"),
                httpConfig.method() != null ? httpConfig.method().toUpperCase() : "POST").toUpperCase();

        try {
            // Build request body from template
            String body = buildRequestBody(httpConfig.requestBodyTemplate(), doc, nodeConfig);

            // Build request
            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(fullUrl))
                    .timeout(Duration.ofMillis(timeoutMs));

            // Headers
            reqBuilder.header("Content-Type", "application/json");
            if (httpConfig.defaultHeaders() != null) {
                httpConfig.defaultHeaders().forEach(reqBuilder::header);
            }

            // Auth token from node config (visual editor)
            Object authToken = nodeConfig.get("authToken");
            if (authToken != null && !authToken.toString().isBlank()) {
                reqBuilder.header("Authorization", "Bearer " + authToken);
            }

            // Method + body
            if ("POST".equals(method)) {
                reqBuilder.POST(HttpRequest.BodyPublishers.ofString(body));
            } else if ("PUT".equals(method)) {
                reqBuilder.PUT(HttpRequest.BodyPublishers.ofString(body));
            } else {
                reqBuilder.GET();
            }

            HttpResponse<String> response = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("[GenericHTTP] {} returned status {} for doc {}", fullUrl, response.statusCode(), doc.getId());
                return AcceleratorResult.miss();
            }

            // Map response to AcceleratorResult
            return mapResponse(response.body(), httpConfig.responseMapping(), nodeTypeKey, nodeConfig);

        } catch (java.net.ConnectException e) {
            log.warn("[GenericHTTP] Service unavailable at {} — skipping for doc {}", fullUrl, doc.getId());
            return AcceleratorResult.miss();
        } catch (java.net.http.HttpTimeoutException e) {
            log.warn("[GenericHTTP] Timed out ({}ms) at {} for doc {}", timeoutMs, fullUrl, doc.getId());
            return AcceleratorResult.miss();
        } catch (Exception e) {
            log.error("[GenericHTTP] Error calling {} for doc {}: {}", fullUrl, doc.getId(), e.getMessage(), e);
            return AcceleratorResult.miss();
        }
    }

    /**
     * Build the request body by interpolating {{placeholders}} in the template.
     */
    private String buildRequestBody(String template, DocumentModel doc, Map<String, Object> nodeConfig) {
        if (template == null || template.isBlank()) {
            // Default body
            var payload = new HashMap<String, Object>();
            payload.put("text", doc.getExtractedText() != null ? doc.getExtractedText() : "");
            payload.put("document_id", doc.getId());
            if (doc.getMimeType() != null) payload.put("mime_type", doc.getMimeType());
            try { return objectMapper.writeValueAsString(payload); } catch (Exception e) { return "{}"; }
        }

        // Interpolate known placeholders
        return template
                .replace("{{extractedText}}", escapeJson(doc.getExtractedText() != null ? doc.getExtractedText() : ""))
                .replace("{{documentId}}", doc.getId() != null ? doc.getId() : "")
                .replace("{{mimeType}}", doc.getMimeType() != null ? doc.getMimeType() : "")
                .replace("{{fileName}}", doc.getOriginalFileName() != null ? doc.getOriginalFileName() : "")
                .replace("{{modelId}}", toString(nodeConfig.get("modelId"), "default"));
    }

    /**
     * Map the JSON response to an AcceleratorResult using the responseMapping.
     */
    private AcceleratorResult mapResponse(String responseBody, Map<String, String> responseMapping,
                                           String nodeTypeKey, Map<String, Object> nodeConfig) throws Exception {
        JsonNode json = objectMapper.readTree(responseBody);

        // Use mapping or fall back to standard field names
        String categoryIdPath = responseMapping != null ? responseMapping.getOrDefault("categoryId", "category_id") : "category_id";
        String categoryNamePath = responseMapping != null ? responseMapping.getOrDefault("categoryName", "category_name") : "category_name";
        String confidencePath = responseMapping != null ? responseMapping.getOrDefault("confidence", "confidence") : "confidence";
        String sensitivityPath = responseMapping != null ? responseMapping.getOrDefault("sensitivityLabel", "sensitivity_label") : "sensitivity_label";
        String reasoningPath = responseMapping != null ? responseMapping.getOrDefault("reasoning", "reasoning") : "reasoning";
        String tagsPath = responseMapping != null ? responseMapping.getOrDefault("tags", "tags") : "tags";

        // Strip "$." prefix if present (JSONPath-style)
        String categoryId = getJsonField(json, stripPrefix(categoryIdPath));
        String categoryName = getJsonField(json, stripPrefix(categoryNamePath));
        double confidence = json.path(stripPrefix(confidencePath)).asDouble(0);
        String sensitivityStr = getJsonField(json, stripPrefix(sensitivityPath));
        String reasoning = getJsonField(json, stripPrefix(reasoningPath));

        List<String> tags = new ArrayList<>();
        JsonNode tagsNode = json.path(stripPrefix(tagsPath));
        if (tagsNode.isArray()) {
            for (JsonNode t : tagsNode) tags.add(t.asText());
        }

        // Check confidence threshold
        double threshold = toDouble(nodeConfig.get("confidenceThreshold"), 0.85);
        if (categoryId == null || confidence < threshold) {
            log.info("[GenericHTTP] Below threshold for {} — confidence: {}, threshold: {}",
                    nodeTypeKey, String.format("%.3f", confidence), threshold);
            return AcceleratorResult.miss();
        }

        SensitivityLabel sensitivity = parseSensitivity(sensitivityStr);

        return AcceleratorResult.hit(
                categoryId, categoryName,
                sensitivity, tags,
                null, confidence,
                reasoning != null ? reasoning : "Generic HTTP classification",
                nodeTypeKey
        );
    }

    private boolean isHostAllowed(String url) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            if (host == null) return false;

            @SuppressWarnings("unchecked")
            List<String> allowedHosts = configService.getValue("pipeline.http.allowed-hosts", DEFAULT_ALLOWED_HOSTS);
            return allowedHosts.stream().anyMatch(allowed -> host.equalsIgnoreCase(allowed));
        } catch (Exception e) {
            return false;
        }
    }

    private SensitivityLabel parseSensitivity(String label) {
        if (label == null) return SensitivityLabel.INTERNAL;
        try { return SensitivityLabel.valueOf(label.toUpperCase()); }
        catch (IllegalArgumentException e) { return SensitivityLabel.INTERNAL; }
    }

    private String getJsonField(JsonNode json, String field) {
        JsonNode node = json.path(field);
        return node.isMissingNode() || node.isNull() ? null : node.asText();
    }

    private String stripPrefix(String path) {
        return path.startsWith("$.") ? path.substring(2) : path;
    }

    private String escapeJson(String text) {
        // Escape for embedding in a JSON string value
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
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
