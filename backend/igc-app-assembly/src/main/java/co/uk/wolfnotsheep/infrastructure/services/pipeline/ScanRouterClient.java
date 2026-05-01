package co.uk.wolfnotsheep.infrastructure.services.pipeline;

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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Synchronous HTTP client to {@code igc-classifier-router}'s
 * {@code POST /v1/classify} for SCAN PROMPT blocks (Phase 1.9 PR2).
 * Sibling to {@link ClassifierRouterClient} — same transport, but a
 * scan-specific result parse: SCAN PROMPTs return
 * {@code {found, instances, confidence}}, not the classification fields
 * the engine's existing client extracts.
 *
 * <p>Active when {@code pipeline.scan-router.enabled=true} (defaults
 * to {@code true} when the classifier router is enabled, but can be
 * toggled independently for staged rollout).
 */
@Service
@ConditionalOnProperty(name = "pipeline.scan-router.enabled", havingValue = "true", matchIfMissing = true)
public class ScanRouterClient {

    private static final Logger log = LoggerFactory.getLogger(ScanRouterClient.class);
    private static final String VALID_TRACEPARENT_TEMPLATE = "00-%s-%s-01";

    private final HttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper();
    private final URI endpoint;
    private final Duration timeout;

    public ScanRouterClient(
            @Value("${pipeline.classifier-router.url:http://igc-classifier-router:8080}") String baseUrl,
            @Value("${pipeline.scan-router.timeout-ms:60000}") int timeoutMs) {
        this.endpoint = URI.create(stripTrailing(baseUrl) + "/v1/classify");
        this.timeout = Duration.ofMillis(timeoutMs);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Run the cascade against the supplied PROMPT block and parse the
     * router's response into a generic shape. Caller wraps the result
     * + per-scan metadata into a {@link PolicyScanResult}.
     *
     * @param nodeRunId      idempotency key per CSV #16 (the scan id +
     *                       run id, deduped by the router for 24h).
     * @param blockId        PROMPT block id (e.g. {@code scan-pii-uk_ni}).
     * @param blockVersion   pinned block version, or null for active.
     * @param extractedText  document text passed inline.
     * @param idempotencyKey {@code Idempotency-Key} header. Same value
     *                       as {@code nodeRunId} is conventional.
     */
    public RouterScanOutcome dispatch(
            String nodeRunId, String blockId, Integer blockVersion,
            String extractedText, String idempotencyKey) {

        Map<String, Object> body = buildRequestBody(nodeRunId, blockId, blockVersion, extractedText);
        HttpRequest req;
        try {
            req = HttpRequest.newBuilder(endpoint)
                    .header("traceparent", randomTraceparent())
                    .header("Idempotency-Key", idempotencyKey == null ? nodeRunId : idempotencyKey)
                    .header("Content-Type", "application/json")
                    .POST(BodyPublishers.ofString(mapper.writeValueAsString(body), StandardCharsets.UTF_8))
                    .timeout(timeout)
                    .build();
        } catch (IOException e) {
            throw new UncheckedIOException("scan request body serialise failed", e);
        }

        long started = System.currentTimeMillis();
        HttpResponse<String> resp;
        try {
            resp = httpClient.send(req, BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            return RouterScanOutcome.failure("scan transport failure: " + e.getMessage(),
                    System.currentTimeMillis() - started);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return RouterScanOutcome.failure("scan call interrupted",
                    System.currentTimeMillis() - started);
        }
        long durationMs = System.currentTimeMillis() - started;

        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            return RouterScanOutcome.failure(
                    "scan HTTP " + resp.statusCode() + ": " + truncate(resp.body(), 1024),
                    durationMs);
        }

        log.debug("[ScanRouterClient] scan success in {}ms (blockId={})", durationMs, blockId);
        return parseSuccess(resp.body(), durationMs);
    }

    private Map<String, Object> buildRequestBody(
            String nodeRunId, String blockId, Integer blockVersion, String extractedText) {
        Map<String, Object> block = new LinkedHashMap<>();
        if (blockId != null) block.put("id", blockId);
        if (blockVersion != null) block.put("version", blockVersion);
        block.put("type", "PROMPT");

        Map<String, Object> text = Map.of(
                "text", extractedText == null ? "" : extractedText,
                "encoding", "utf-8");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("nodeRunId", nodeRunId);
        body.put("block", block);
        body.put("text", text);
        return body;
    }

    private RouterScanOutcome parseSuccess(String json, long durationMs) {
        try {
            JsonNode root = mapper.readTree(json);
            String tier = optText(root, "tierOfDecision");
            Double confidence = root.has("confidence") && !root.path("confidence").isNull()
                    ? root.path("confidence").asDouble()
                    : null;
            Map<String, Object> result = readObjectMap(root.path("result"));
            return new RouterScanOutcome(true, tier, confidence,
                    result == null ? Map.of() : result, null, durationMs);
        } catch (IOException e) {
            return RouterScanOutcome.failure("scan response parse failed: " + e.getMessage(), durationMs);
        }
    }

    private static String optText(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isMissingNode() || v.isNull() ? null : v.asText();
    }

    private static Map<String, Object> readObjectMap(JsonNode node) {
        if (node == null || !node.isObject()) return null;
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

    /**
     * Generic outcome of a router scan call. The dispatcher wraps this
     * with per-scan metadata (scanType, ref, blocking) into a
     * {@link PolicyScanResult}.
     */
    public record RouterScanOutcome(
            boolean success,
            String tierOfDecision,
            Double confidence,
            Map<String, Object> result,
            String error,
            long durationMs) {

        public static RouterScanOutcome failure(String error, long durationMs) {
            return new RouterScanOutcome(false, null, null, Map.of(), error, durationMs);
        }
    }
}
