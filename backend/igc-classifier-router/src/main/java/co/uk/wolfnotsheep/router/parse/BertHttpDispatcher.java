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
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Synchronous HTTP client to {@code igc-bert-inference}'s
 * {@code POST /v1/infer}. Pure transport — no orchestration.
 *
 * <p>Exception contract:
 *
 * <ul>
 *     <li>{@code 200} → {@link BertInferenceResult}.</li>
 *     <li>{@code 503} (any code, but typically
 *         {@code MODEL_NOT_LOADED}) → {@link BertTierFallthroughException}
 *         — the inference replica has no artefact loaded; the cascade
 *         should escalate to the next tier.</li>
 *     <li>{@code 422} → {@link BertBlockUnknownException} — the
 *         block coords don't resolve; this is a configuration error
 *         that should surface, not silently fall through.</li>
 *     <li>Other 4xx / 5xx → {@link BertTierFallthroughException}
 *         with an {@code errorCode} matching the upstream code; the
 *         cascade escalates rather than surfacing transient BERT
 *         failures (the LLM tier is the architectural floor).</li>
 *     <li>Transport / parse failure →
 *         {@link BertTierFallthroughException} with errorCode
 *         {@code BERT_TRANSPORT_ERROR}; same rationale.</li>
 * </ul>
 *
 * <p>The 200 path returns a {@link BertInferenceResult}; the
 * {@link BertOrchestratorCascadeService} composes that into the
 * cascade response shape.
 */
public class BertHttpDispatcher {

    private static final Logger log = LoggerFactory.getLogger(BertHttpDispatcher.class);

    private final HttpClient http;
    private final URI inferEndpoint;
    private final Duration timeout;
    private final ObjectMapper mapper;

    public BertHttpDispatcher(HttpClient http, URI baseUrl, Duration timeout) {
        this(http, baseUrl, timeout, new ObjectMapper());
    }

    public BertHttpDispatcher(HttpClient http, URI baseUrl, Duration timeout, ObjectMapper mapper) {
        this.http = http;
        this.inferEndpoint = baseUrl.resolve("/v1/infer");
        this.timeout = timeout;
        this.mapper = mapper;
    }

    /**
     * Dispatch one inference. {@code blockType} is not on the wire —
     * the BERT contract carries only the block coord and text — but
     * the orchestrator only calls into this for BERT_CLASSIFIER blocks.
     */
    public BertInferenceResult infer(String blockId, Integer blockVersion,
                                     String nodeRunId, String text) {
        Map<String, Object> body = buildRequestBody(blockId, blockVersion, nodeRunId, text);
        HttpRequest req;
        try {
            req = HttpRequest.newBuilder(inferEndpoint)
                    .header("Content-Type", "application/json")
                    .POST(BodyPublishers.ofString(
                            mapper.writeValueAsString(body), StandardCharsets.UTF_8))
                    .timeout(timeout)
                    .build();
        } catch (IOException e) {
            throw new BertTierFallthroughException(
                    "BERT_TRANSPORT_ERROR",
                    "BERT request body serialise failed: " + e.getMessage(), e);
        }

        HttpResponse<String> resp;
        try {
            resp = http.send(req, BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new BertTierFallthroughException(
                    "BERT_TRANSPORT_ERROR",
                    "BERT transport failure: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BertTierFallthroughException(
                    "BERT_TRANSPORT_ERROR",
                    "BERT call interrupted: " + e.getMessage(), e);
        }

        int status = resp.statusCode();
        if (status == 200) {
            return parseSuccess(resp.body());
        }
        if (status == 503) {
            String code = extractCode(resp.body(), "MODEL_NOT_LOADED");
            log.debug("router: BERT tier 503 ({}); falling through", code);
            throw new BertTierFallthroughException(code, truncate(resp.body(), 256));
        }
        if (status == 422) {
            throw new BertBlockUnknownException(
                    "BERT block coords did not resolve: " + truncate(resp.body(), 256));
        }
        // Any other 4xx / 5xx — fall through. We log loudly so ops sees it.
        String code = "BERT_HTTP_" + status;
        log.warn("router: BERT tier returned HTTP {}; falling through. body={}",
                status, truncate(resp.body(), 256));
        throw new BertTierFallthroughException(code, truncate(resp.body(), 256));
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
        body.put("block", block);
        body.put("text", inline);
        if (nodeRunId != null) body.put("nodeRunId", nodeRunId);
        return body;
    }

    private BertInferenceResult parseSuccess(String json) {
        try {
            JsonNode root = mapper.readTree(json);
            String label = optText(root, "label");
            float confidence = (float) root.path("confidence").asDouble(0.0);
            String modelVersion = optText(root, "modelVersion");
            if (label == null) {
                throw new BertTierFallthroughException(
                        "BERT_RESPONSE_INVALID",
                        "BERT 200 response missing label: " + truncate(json, 256));
            }
            return new BertInferenceResult(label, confidence, modelVersion);
        } catch (IOException e) {
            throw new BertTierFallthroughException(
                    "BERT_RESPONSE_INVALID",
                    "BERT response parse failed: " + e.getMessage(), e);
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

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
