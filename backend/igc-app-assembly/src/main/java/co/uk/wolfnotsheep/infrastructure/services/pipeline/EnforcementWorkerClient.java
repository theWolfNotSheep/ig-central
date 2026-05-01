package co.uk.wolfnotsheep.infrastructure.services.pipeline;

import co.uk.wolfnotsheep.document.events.DocumentClassifiedEvent;
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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Synchronous HTTP client to {@code igc-enforcement-worker}'s
 * {@code POST /v1/enforce}. Active when
 * {@code pipeline.enforcement-worker.enabled=true}.
 *
 * <p>Wraps the contract surface from
 * {@code contracts/enforcement-worker/openapi.yaml} v0.2.0 +
 * surfaces a slim {@link Outcome} record so the engine treats the
 * worker call as a transport swap, not a state-machine rework. The
 * worker writes the document to Mongo internally (via the in-place
 * {@code EnforcementService}); the engine re-fetches the doc by id
 * after this call to apply per-node toggle clearing + status, same
 * shape as the legacy in-process path.
 *
 * <p>Mirrors {@link ClassifierRouterClient} so observers and ops
 * tooling can treat the cutover transports identically.
 */
@Service
@ConditionalOnProperty(name = "pipeline.enforcement-worker.enabled", havingValue = "true")
public class EnforcementWorkerClient {

    private static final Logger log = LoggerFactory.getLogger(EnforcementWorkerClient.class);
    private static final String VALID_TRACEPARENT_TEMPLATE = "00-%s-%s-01";

    private final HttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper();
    private final URI endpoint;
    private final Duration timeout;

    public EnforcementWorkerClient(
            @Value("${pipeline.enforcement-worker.url:http://igc-enforcement-worker:8097}") String baseUrl,
            @Value("${pipeline.enforcement-worker.timeout-ms:60000}") int timeoutMs) {
        this.endpoint = URI.create(stripTrailing(baseUrl) + "/v1/enforce");
        this.timeout = Duration.ofMillis(timeoutMs);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Call the worker and return a slim {@link Outcome} mirroring the
     * contract {@code AppliedSummary}. The engine re-fetches the
     * document via {@code DocumentService.getById} after this call —
     * the worker has already persisted state by the time this returns.
     *
     * @param nodeRunId pipeline-node run id (idempotency key per CSV #16).
     * @param event     classification event (same shape the in-process
     *                  {@code EnforcementService.enforce} consumes).
     * @return parsed worker outcome.
     * @throws EnforcementWorkerException on transport or non-2xx
     *         response. The engine treats this the same as any other
     *         enforcement-stage error (per CLAUDE.md happy/unhappy-path).
     */
    public Outcome enforce(String nodeRunId, DocumentClassifiedEvent event) {
        Map<String, Object> body = buildRequestBody(nodeRunId, event);
        HttpRequest req;
        try {
            req = HttpRequest.newBuilder(endpoint)
                    .header("traceparent", randomTraceparent())
                    .header("Idempotency-Key", "nodeRun:" + nodeRunId)
                    .header("Content-Type", "application/json")
                    .POST(BodyPublishers.ofString(mapper.writeValueAsString(body), StandardCharsets.UTF_8))
                    .timeout(timeout)
                    .build();
        } catch (IOException e) {
            throw new UncheckedIOException("enforcement-worker request body serialise failed", e);
        }

        long started = System.currentTimeMillis();
        HttpResponse<String> resp;
        try {
            resp = httpClient.send(req, BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new EnforcementWorkerException("enforcement-worker transport failure", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EnforcementWorkerException("enforcement-worker call interrupted", e);
        }
        long durationMs = System.currentTimeMillis() - started;

        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new EnforcementWorkerException(
                    "enforcement-worker HTTP " + resp.statusCode() + ": " + truncate(resp.body(), 1024));
        }

        log.debug("[EnforcementWorkerClient] enforce success in {}ms (nodeRunId={}, doc={})",
                durationMs, nodeRunId, event.documentId());
        return parseResponse(resp.body(), nodeRunId, event.documentId());
    }

    private Map<String, Object> buildRequestBody(String nodeRunId, DocumentClassifiedEvent event) {
        Map<String, Object> classification = new LinkedHashMap<>();
        classification.put("documentId", event.documentId());
        classification.put("classificationResultId", event.classificationResultId());
        classification.put("categoryId", event.categoryId());
        if (event.categoryName() != null) classification.put("categoryName", event.categoryName());
        if (event.sensitivityLabel() != null) {
            classification.put("sensitivityLabel", event.sensitivityLabel().name());
        }
        if (event.tags() != null) classification.put("tags", event.tags());
        if (event.applicablePolicyIds() != null) {
            classification.put("applicablePolicyIds", event.applicablePolicyIds());
        }
        if (event.retentionScheduleId() != null) {
            classification.put("retentionScheduleId", event.retentionScheduleId());
        }
        classification.put("confidence", (float) event.confidence());
        classification.put("requiresHumanReview", event.requiresHumanReview());
        if (event.classifiedAt() != null) {
            classification.put("classifiedAt", event.classifiedAt().toString());
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("nodeRunId", nodeRunId);
        body.put("classification", classification);
        return body;
    }

    private Outcome parseResponse(String json, String nodeRunId, String documentId) {
        try {
            JsonNode root = mapper.readTree(json);
            JsonNode applied = root.path("applied");
            return new Outcome(
                    optText(root, "nodeRunId", nodeRunId),
                    optText(root, "documentId", documentId),
                    optText(applied, "storageTierBefore", null),
                    optText(applied, "storageTierAfter", null),
                    applied.path("storageMigrated").asBoolean(false),
                    optText(applied, "auditEventId", null),
                    root.path("durationMs").asLong(0L));
        } catch (IOException e) {
            throw new EnforcementWorkerException(
                    "enforcement-worker response parse failed: " + e.getMessage(), e);
        }
    }

    private static String optText(JsonNode node, String field, String fallback) {
        JsonNode v = node.path(field);
        return v.isMissingNode() || v.isNull() ? fallback : v.asText();
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
     * Slim view of the contract {@code EnforceResponse + AppliedSummary}.
     * Used by the engine for observability + status routing; the
     * canonical document state lives in Mongo (the worker writes
     * before returning).
     */
    public record Outcome(
            String nodeRunId,
            String documentId,
            String storageTierBefore,
            String storageTierAfter,
            boolean storageMigrated,
            String auditEventId,
            long durationMs) {}
}
