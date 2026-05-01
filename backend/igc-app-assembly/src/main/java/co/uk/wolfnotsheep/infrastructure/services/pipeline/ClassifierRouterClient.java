package co.uk.wolfnotsheep.infrastructure.services.pipeline;

import co.uk.wolfnotsheep.document.events.LlmJobCompletedEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Synchronous HTTP client to {@code igc-classifier-router}'s
 * {@code POST /v1/classify}. Active when
 * {@code pipeline.classifier-router.enabled=true}.
 *
 * <p>Translates the router's {@code ClassifyResponse} back into the
 * existing {@link LlmJobCompletedEvent} shape so the engine's
 * {@code resumePipeline} can apply the result without changes — the
 * cutover is a transport swap, not a state-machine rework.
 *
 * <p>The router itself dispatches via the existing LLM worker over
 * Rabbit (its {@code LlmDispatchCascadeService}), so the underlying
 * model call is unchanged. The only behavioural difference is that
 * the engine thread holds the request open instead of releasing on
 * a Rabbit publish — bounded by the router's
 * {@code igc.router.cascade.llm.timeout} (default 60s) plus the
 * {@code pipeline.classifier-router.timeout-ms} we set here.
 */
@Service
@ConditionalOnProperty(name = "pipeline.classifier-router.enabled", havingValue = "true")
public class ClassifierRouterClient {

    private static final Logger log = LoggerFactory.getLogger(ClassifierRouterClient.class);
    private static final String VALID_TRACEPARENT_TEMPLATE =
            "00-%s-%s-01";

    private final HttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper();
    private final URI endpoint;
    private final Duration timeout;

    public ClassifierRouterClient(
            @Value("${pipeline.classifier-router.url:http://igc-classifier-router:8080}") String baseUrl,
            @Value("${pipeline.classifier-router.timeout-ms:90000}") int timeoutMs) {
        this.endpoint = URI.create(stripTrailing(baseUrl) + "/v1/classify");
        this.timeout = Duration.ofMillis(timeoutMs);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Call the router and synthesise an {@link LlmJobCompletedEvent}
     * matching the existing engine shape.
     *
     * @param jobId          job correlation id (also placed on the event).
     * @param pipelineRunId  pipeline run id (echoed on the event).
     * @param nodeRunId      node run id (idempotency key per CSV #16;
     *                       lands on the event).
     * @param blockId        PROMPT / BERT_CLASSIFIER block id; null is
     *                       allowed but will likely yield a 422 from the
     *                       router once block validation lands.
     * @param blockVersion   pinned block version, or null for active.
     * @param extractedText  the document's extracted text (passes
     *                       inline to the router — router may textRef
     *                       to MinIO in a future contract bump).
     * @param idempotencyKey same value the existing async path passes;
     *                       the router uses this for {@code nodeRunId}-keyed
     *                       idempotency.
     * @return populated {@link LlmJobCompletedEvent}.
     * @throws ClassifierRouterException on transport / response
     *         failure. The engine treats this the same as any other
     *         LLM-stage error.
     */
    public LlmJobCompletedEvent classify(
            String jobId, String pipelineRunId, String nodeRunId,
            String blockId, Integer blockVersion,
            String extractedText, String idempotencyKey) {

        Map<String, Object> body = buildRequestBody(nodeRunId, blockId, blockVersion, extractedText);
        HttpRequest req;
        try {
            req = HttpRequest.newBuilder(endpoint)
                    .header("traceparent", randomTraceparent())
                    .header("Idempotency-Key", idempotencyKey)
                    .header("Content-Type", "application/json")
                    .POST(BodyPublishers.ofString(mapper.writeValueAsString(body), StandardCharsets.UTF_8))
                    .timeout(timeout)
                    .build();
        } catch (IOException e) {
            throw new UncheckedIOException("router request body serialise failed", e);
        }

        long started = System.currentTimeMillis();
        HttpResponse<String> resp;
        try {
            resp = httpClient.send(req, BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new ClassifierRouterException("router transport failure", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ClassifierRouterException("router call interrupted", e);
        }
        long durationMs = System.currentTimeMillis() - started;

        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            return LlmJobCompletedEvent.failure(jobId, pipelineRunId, nodeRunId,
                    "router HTTP " + resp.statusCode() + ": " + truncate(resp.body(), 1024));
        }

        log.debug("[ClassifierRouterClient] classify success in {}ms (jobId={})", durationMs, jobId);
        return parseResponse(resp.body(), jobId, pipelineRunId, nodeRunId);
    }

    private Map<String, Object> buildRequestBody(
            String nodeRunId, String blockId, Integer blockVersion, String extractedText) {
        Map<String, Object> block = new java.util.LinkedHashMap<>();
        if (blockId != null) block.put("id", blockId);
        if (blockVersion != null) block.put("version", blockVersion);
        block.put("type", "PROMPT");

        Map<String, Object> text = Map.of(
                "text", extractedText == null ? "" : extractedText,
                "encoding", "utf-8");

        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("nodeRunId", nodeRunId);
        body.put("block", block);
        body.put("text", text);
        return body;
    }

    private LlmJobCompletedEvent parseResponse(
            String json, String jobId, String pipelineRunId, String nodeRunId) {
        try {
            JsonNode root = mapper.readTree(json);
            JsonNode result = root.path("result");
            String categoryId = optText(result, "categoryId");
            String categoryName = optText(result, "category");
            String sensitivityLabel = optText(result, "sensitivity");
            double confidence = root.path("confidence").asDouble(0.0);
            boolean requiresHumanReview = result.path("requiresHumanReview").asBoolean(false);
            String retentionScheduleId = optText(result, "retentionScheduleId");

            List<String> tags = readStringArray(result.path("tags"));
            List<String> applicablePolicyIds = readStringArray(result.path("applicablePolicyIds"));
            Map<String, Object> extractedMetadata = readObjectMap(result.path("extractedMetadata"));
            Map<String, Object> customResult = readObjectMap(result.path("customResult"));

            return new LlmJobCompletedEvent(
                    jobId, pipelineRunId, nodeRunId,
                    /* success */ true,
                    /* classificationResultId */ null,
                    categoryId, categoryName, sensitivityLabel,
                    tags.isEmpty() ? null : tags,
                    confidence,
                    requiresHumanReview,
                    retentionScheduleId,
                    applicablePolicyIds.isEmpty() ? null : applicablePolicyIds,
                    extractedMetadata,
                    customResult,
                    /* error */ null,
                    Instant.now());
        } catch (IOException e) {
            return LlmJobCompletedEvent.failure(jobId, pipelineRunId, nodeRunId,
                    "router response parse failed: " + e.getMessage());
        }
    }

    private static String optText(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isMissingNode() || v.isNull() ? null : v.asText();
    }

    private static List<String> readStringArray(JsonNode arrayNode) {
        if (arrayNode == null || !arrayNode.isArray()) return List.of();
        List<String> out = new ArrayList<>(arrayNode.size());
        for (JsonNode n : arrayNode) out.add(n.asText());
        return out;
    }

    private static Map<String, Object> readObjectMap(JsonNode node) {
        if (node == null || !node.isObject()) return null;
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> it = node.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> e = it.next();
            out.put(e.getKey(), unwrap(e.getValue()));
        }
        return out;
    }

    private static Object unwrap(JsonNode node) {
        if (node.isTextual()) return node.asText();
        if (node.isInt() || node.isLong()) return node.asLong();
        if (node.isDouble() || node.isFloat()) return node.asDouble();
        if (node.isBoolean()) return node.asBoolean();
        if (node.isArray()) {
            List<Object> out = new ArrayList<>(node.size());
            for (JsonNode n : node) out.add(unwrap(n));
            return out;
        }
        if (node.isObject()) return readObjectMap(node);
        return null;
    }

    private static String randomTraceparent() {
        String trace = randomHex(32);
        String span = randomHex(16);
        return String.format(VALID_TRACEPARENT_TEMPLATE, trace, span);
    }

    private static String randomHex(int len) {
        String s = UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
        return s.substring(0, len);
    }

    private static String stripTrailing(String url) {
        if (url == null) return "";
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
