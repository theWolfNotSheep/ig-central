package co.uk.wolfnotsheep.infrastructure.services;

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
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

/**
 * Synchronous HTTP client to {@code igc-indexing-worker}'s admin
 * surface. Active when {@code pipeline.indexing-worker.enabled=true};
 * when the bean is absent, the legacy {@link ElasticsearchIndexService}
 * keeps writing to ES in-process.
 *
 * <p>Mirrors {@link co.uk.wolfnotsheep.infrastructure.services.pipeline.ClassifierRouterClient}
 * + the enforcement-worker client so observers and ops tooling can
 * treat the cutover transports identically.
 *
 * <p>The client wraps three operations:
 * <ul>
 *   <li>{@link #indexDocument(String)} → {@code POST /v1/index/{id}?nodeRunId=…}</li>
 *   <li>{@link #removeDocument(String)} → {@code DELETE /v1/index/{id}}</li>
 *   <li>{@link #reindex()} → {@code POST /v1/reindex} (fire + forget;
 *       returns the {@code nodeRunId} for the caller to poll if it
 *       cares about the summary)</li>
 * </ul>
 */
@Service
@ConditionalOnProperty(name = "pipeline.indexing-worker.enabled", havingValue = "true")
public class IndexingWorkerClient {

    private static final Logger log = LoggerFactory.getLogger(IndexingWorkerClient.class);
    private static final String VALID_TRACEPARENT_TEMPLATE = "00-%s-%s-01";

    private final HttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper();
    private final String baseUrl;
    private final Duration timeout;

    public IndexingWorkerClient(
            @Value("${pipeline.indexing-worker.url:http://igc-indexing-worker:8098}") String baseUrl,
            @Value("${pipeline.indexing-worker.timeout-ms:30000}") int timeoutMs) {
        this.baseUrl = stripTrailing(baseUrl);
        this.timeout = Duration.ofMillis(timeoutMs);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Sync index a single document. Returns the ES {@code _version}
     * after upsert (best-effort, 0 if the worker omits it).
     */
    public long indexDocument(String documentId) {
        String nodeRunId = "index-" + documentId + "-" + System.currentTimeMillis();
        URI uri = URI.create(baseUrl + "/v1/index/" + URLEncoder.encode(documentId, StandardCharsets.UTF_8)
                + "?nodeRunId=" + URLEncoder.encode(nodeRunId, StandardCharsets.UTF_8));
        HttpRequest req = HttpRequest.newBuilder(uri)
                .header("traceparent", randomTraceparent())
                .header("Idempotency-Key", "nodeRun:" + nodeRunId)
                .header("Content-Type", "application/json")
                .POST(BodyPublishers.noBody())
                .timeout(timeout)
                .build();

        HttpResponse<String> resp = send(req, "index " + documentId);
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new IndexingWorkerException(
                    "indexing-worker HTTP " + resp.statusCode() + " for index " + documentId
                            + ": " + truncate(resp.body(), 512));
        }
        return parseVersion(resp.body());
    }

    /** Delete a document from the index. Idempotent. */
    public String removeDocument(String documentId) {
        URI uri = URI.create(baseUrl + "/v1/index/" + URLEncoder.encode(documentId, StandardCharsets.UTF_8));
        HttpRequest req = HttpRequest.newBuilder(uri)
                .header("traceparent", randomTraceparent())
                .DELETE()
                .timeout(timeout)
                .build();
        HttpResponse<String> resp = send(req, "delete " + documentId);
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new IndexingWorkerException(
                    "indexing-worker HTTP " + resp.statusCode() + " for delete " + documentId
                            + ": " + truncate(resp.body(), 512));
        }
        return parseField(resp.body(), "result", "DELETED");
    }

    /**
     * Kick off an async bulk reindex. Returns the {@code nodeRunId}
     * for the caller to poll {@code GET /v1/jobs/{nodeRunId}}; the
     * legacy {@link ElasticsearchIndexService#reindexAll()} surface
     * returned an int count, so the legacy facade waits-and-polls or
     * blocks here. We choose fire-and-forget — admin reindex is
     * long-running and the caller can refresh the monitoring page.
     */
    public String reindex() {
        String nodeRunId = "reindex-" + System.currentTimeMillis();
        String body = "{\"nodeRunId\":\"" + nodeRunId + "\"}";
        URI uri = URI.create(baseUrl + "/v1/reindex");
        HttpRequest req = HttpRequest.newBuilder(uri)
                .header("traceparent", randomTraceparent())
                .header("Idempotency-Key", "nodeRun:" + nodeRunId)
                .header("Content-Type", "application/json")
                .POST(BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .timeout(timeout)
                .build();
        HttpResponse<String> resp = send(req, "reindex dispatch");
        if (resp.statusCode() != 202) {
            throw new IndexingWorkerException(
                    "indexing-worker HTTP " + resp.statusCode() + " for reindex dispatch: "
                            + truncate(resp.body(), 512));
        }
        return nodeRunId;
    }

    private HttpResponse<String> send(HttpRequest req, String op) {
        try {
            HttpResponse<String> resp = httpClient.send(req, BodyHandlers.ofString(StandardCharsets.UTF_8));
            log.debug("[IndexingWorkerClient] {} returned HTTP {}", op, resp.statusCode());
            return resp;
        } catch (IOException e) {
            throw new IndexingWorkerException("indexing-worker transport failure on " + op, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IndexingWorkerException("indexing-worker call interrupted on " + op, e);
        }
    }

    private long parseVersion(String body) {
        try {
            JsonNode root = mapper.readTree(body);
            return root.path("version").asLong(0L);
        } catch (IOException e) {
            throw new UncheckedIOException("indexing-worker response parse failed", e);
        }
    }

    private String parseField(String body, String field, String fallback) {
        try {
            JsonNode root = mapper.readTree(body);
            JsonNode v = root.path(field);
            return v.isMissingNode() || v.isNull() ? fallback : v.asText(fallback);
        } catch (IOException e) {
            return fallback;
        }
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
