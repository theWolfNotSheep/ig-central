package co.uk.wolfnotsheep.router.parse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Synchronous HTTP client to {@code gls-llm-worker}'s
 * {@code POST /v1/classify}. Pure transport — no orchestration. Same
 * shape as {@link SlmHttpDispatcher} (which served as the template).
 *
 * <p>Exception contract:
 *
 * <ul>
 *     <li>{@code 200} → {@link LlmInferenceResult}.</li>
 *     <li>{@code 503} → {@link LlmTierFallthroughException}
 *         (errorCode from body, default {@code LLM_NOT_CONFIGURED}).</li>
 *     <li>{@code 422} → {@link LlmBlockUnknownException} — surfaces
 *         as 422 {@code ROUTER_LLM_BLOCK_UNKNOWN}.</li>
 *     <li>Other 4xx / 5xx → {@link LlmTierFallthroughException} with
 *         {@code errorCode=LLM_HTTP_<status>}.</li>
 *     <li>Transport / parse failure →
 *         {@link LlmTierFallthroughException} with
 *         {@code errorCode=LLM_TRANSPORT_ERROR}.</li>
 * </ul>
 */
public class LlmHttpDispatcher {

    private static final Logger log = LoggerFactory.getLogger(LlmHttpDispatcher.class);

    private final HttpClient http;
    private final URI classifyEndpoint;
    private final Duration timeout;
    private final ObjectMapper mapper;

    public LlmHttpDispatcher(HttpClient http, URI baseUrl, Duration timeout) {
        this(http, baseUrl, timeout, new ObjectMapper());
    }

    public LlmHttpDispatcher(HttpClient http, URI baseUrl, Duration timeout, ObjectMapper mapper) {
        this.http = http;
        this.classifyEndpoint = baseUrl.resolve("/v1/classify");
        this.timeout = timeout;
        this.mapper = mapper;
    }

    public LlmInferenceResult classify(String blockId, Integer blockVersion,
                                       String nodeRunId, String text) {
        Map<String, Object> body = buildRequestBody(blockId, blockVersion, nodeRunId, text);
        HttpRequest req;
        try {
            req = HttpRequest.newBuilder(classifyEndpoint)
                    .header("Content-Type", "application/json")
                    .POST(BodyPublishers.ofString(
                            mapper.writeValueAsString(body), StandardCharsets.UTF_8))
                    .timeout(timeout)
                    .build();
        } catch (IOException e) {
            throw new LlmTierFallthroughException(
                    "LLM_TRANSPORT_ERROR",
                    "LLM request body serialise failed: " + e.getMessage(), e);
        }

        HttpResponse<String> resp;
        try {
            resp = http.send(req, BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new LlmTierFallthroughException(
                    "LLM_TRANSPORT_ERROR",
                    "LLM transport failure: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmTierFallthroughException(
                    "LLM_TRANSPORT_ERROR",
                    "LLM call interrupted: " + e.getMessage(), e);
        }

        int status = resp.statusCode();
        if (status == 200) {
            return parseSuccess(resp.body());
        }
        if (status == 503) {
            String code = extractCode(resp.body(), "LLM_NOT_CONFIGURED");
            log.debug("router: LLM tier 503 ({})", code);
            throw new LlmTierFallthroughException(code, truncate(resp.body(), 256));
        }
        if (status == 422) {
            throw new LlmBlockUnknownException(
                    "LLM block coords did not resolve: " + truncate(resp.body(), 256));
        }
        String code = "LLM_HTTP_" + status;
        log.warn("router: LLM tier returned HTTP {}; treating as failure. body={}",
                status, truncate(resp.body(), 256));
        throw new LlmTierFallthroughException(code, truncate(resp.body(), 256));
    }

    private Map<String, Object> buildRequestBody(
            String blockId, Integer blockVersion, String nodeRunId, String text) {
        Map<String, Object> block = new LinkedHashMap<>();
        if (blockId != null) block.put("id", blockId);
        if (blockVersion != null) block.put("version", blockVersion);

        Map<String, Object> inline = new LinkedHashMap<>();
        inline.put("text", text == null ? "" : text);
        inline.put("encoding", "utf-8");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("nodeRunId", nodeRunId == null ? "router-direct" : nodeRunId);
        body.put("block", block);
        body.put("text", inline);
        return body;
    }

    private LlmInferenceResult parseSuccess(String json) {
        try {
            JsonNode root = mapper.readTree(json);
            float confidence = (float) root.path("confidence").asDouble(0.0);
            String modelId = optText(root, "modelId");
            long tokensIn = root.path("tokensIn").asLong(0L);
            long tokensOut = root.path("tokensOut").asLong(0L);
            long costUnits = root.path("costUnits").asLong(0L);

            JsonNode resultNode = root.path("result");
            Map<String, Object> result = resultNode.isObject()
                    ? toMap(resultNode)
                    : new LinkedHashMap<>();

            return new LlmInferenceResult(result, confidence, modelId,
                    tokensIn, tokensOut, costUnits);
        } catch (IOException e) {
            throw new LlmTierFallthroughException(
                    "LLM_RESPONSE_INVALID",
                    "LLM response parse failed: " + e.getMessage(), e);
        }
    }

    private String extractCode(String body, String fallback) {
        if (body == null || body.isBlank()) return fallback;
        try {
            JsonNode root = mapper.readTree(body);
            String code = optText(root, "code");
            return code == null ? fallback : code;
        } catch (IOException e) {
            return fallback;
        }
    }

    private static String optText(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isMissingNode() || v.isNull() ? null : v.asText();
    }

    private static Map<String, Object> toMap(JsonNode node) {
        Map<String, Object> out = new LinkedHashMap<>();
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
        if (node.isObject()) return toMap(node);
        return null;
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
