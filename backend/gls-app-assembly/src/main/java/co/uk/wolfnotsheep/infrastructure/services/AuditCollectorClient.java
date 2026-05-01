package co.uk.wolfnotsheep.infrastructure.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Read-only HTTP client to {@code gls-audit-collector}'s {@code /v1/events}
 * surface. Used by the admin audit-explorer to surface Tier 1 / Tier 2
 * envelope events on the operator UI.
 *
 * <p>Mirrors the existing {@link IndexingWorkerClient} shape — JDK
 * {@link HttpClient}, no Spring HTTP abstraction, configurable base URL,
 * traceparent header, configurable timeout. Returns raw JSON strings
 * from the upstream service so the controller can pass them through
 * unmodified — saves a DTO mapping layer that would otherwise need to
 * track the audit-collector contract version.
 */
@Service
public class AuditCollectorClient {

    private static final Logger log = LoggerFactory.getLogger(AuditCollectorClient.class);
    private static final String VALID_TRACEPARENT_TEMPLATE = "00-%s-%s-01";

    private final HttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper();
    private final String baseUrl;
    private final Duration timeout;

    public AuditCollectorClient(
            @Value("${gls.audit.collector.url:http://gls-audit-collector:8080}") String baseUrl,
            @Value("${gls.audit.collector.timeout-ms:10000}") int timeoutMs) {
        this.baseUrl = stripTrailing(baseUrl);
        this.timeout = Duration.ofMillis(timeoutMs);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    /**
     * Search Tier 2 events. All filters are optional. Returns the raw
     * upstream JSON envelope (events list + nextPageToken) so the
     * caller can forward it untouched.
     */
    public String listTier2Events(SearchParams params) {
        StringBuilder query = new StringBuilder("?");
        appendIfPresent(query, "documentId", params.documentId());
        appendIfPresent(query, "eventType", params.eventType());
        appendIfPresent(query, "actorService", params.actorService());
        appendIfPresent(query, "from", params.from());
        appendIfPresent(query, "to", params.to());
        appendIfPresent(query, "pageToken", params.pageToken());
        if (params.pageSize() != null) {
            query.append("pageSize=").append(params.pageSize()).append("&");
        }
        URI uri = URI.create(baseUrl + "/v1/events" + (query.length() > 1
                ? query.substring(0, query.length() - 1) : ""));
        HttpRequest req = HttpRequest.newBuilder(uri)
                .header("traceparent", randomTraceparent())
                .header("Accept", "application/json")
                .timeout(timeout)
                .GET()
                .build();
        HttpResponse<String> resp = send(req, "listEvents");
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new AuditCollectorException(
                    "audit-collector HTTP " + resp.statusCode() + " on listEvents: "
                            + truncate(resp.body(), 512));
        }
        return resp.body();
    }

    /**
     * Verify a Tier 1 hash chain for a resource. Returns the upstream
     * JSON ({@code status}, {@code eventsTraversed}, etc.) verbatim or
     * {@link Optional#empty()} on a 404 (no Tier 1 events for this
     * resource — which is a "valid" outcome, not an error).
     */
    public Optional<String> verifyChain(String resourceType, String resourceId) {
        URI uri = URI.create(baseUrl + "/v1/chains/"
                + URLEncoder.encode(resourceType, StandardCharsets.UTF_8) + "/"
                + URLEncoder.encode(resourceId, StandardCharsets.UTF_8) + "/verify");
        HttpRequest req = HttpRequest.newBuilder(uri)
                .header("traceparent", randomTraceparent())
                .header("Accept", "application/json")
                .timeout(timeout)
                .GET()
                .build();
        HttpResponse<String> resp = send(req, "verifyChain " + resourceType + ":" + resourceId);
        if (resp.statusCode() == 404) return Optional.empty();
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new AuditCollectorException(
                    "audit-collector HTTP " + resp.statusCode() + " on verifyChain: "
                            + truncate(resp.body(), 512));
        }
        return Optional.of(resp.body());
    }

    /**
     * Single-event lookup by id. Returns empty when audit-collector
     * responds 404 — the caller treats it as a not-found rather than
     * a transport failure.
     */
    public Optional<String> findEventById(String eventId) {
        URI uri = URI.create(baseUrl + "/v1/events/"
                + URLEncoder.encode(eventId, StandardCharsets.UTF_8));
        HttpRequest req = HttpRequest.newBuilder(uri)
                .header("traceparent", randomTraceparent())
                .header("Accept", "application/json")
                .timeout(timeout)
                .GET()
                .build();
        HttpResponse<String> resp = send(req, "getEvent " + eventId);
        if (resp.statusCode() == 404) return Optional.empty();
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new AuditCollectorException(
                    "audit-collector HTTP " + resp.statusCode() + " on getEvent: "
                            + truncate(resp.body(), 512));
        }
        return Optional.of(resp.body());
    }

    private HttpResponse<String> send(HttpRequest req, String op) {
        try {
            HttpResponse<String> resp = httpClient.send(req, BodyHandlers.ofString(StandardCharsets.UTF_8));
            log.debug("[AuditCollectorClient] {} returned HTTP {}", op, resp.statusCode());
            return resp;
        } catch (IOException e) {
            throw new AuditCollectorException("audit-collector transport failure on " + op, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AuditCollectorException("audit-collector call interrupted on " + op, e);
        }
    }

    private static void appendIfPresent(StringBuilder query, String key, String value) {
        if (value != null && !value.isBlank()) {
            query.append(key).append("=")
                    .append(URLEncoder.encode(value, StandardCharsets.UTF_8))
                    .append("&");
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

    /** Optional filter set for Tier 2 search. All fields nullable / blank-tolerant. */
    public record SearchParams(
            String documentId,
            String eventType,
            String actorService,
            String from,
            String to,
            String pageToken,
            Integer pageSize) {}

    public static class AuditCollectorException extends RuntimeException {
        public AuditCollectorException(String msg) { super(msg); }
        public AuditCollectorException(String msg, Throwable cause) { super(msg, cause); }
    }

    /** Visible for testing. */
    JsonNode parseJson(String body) {
        try {
            return mapper.readTree(body);
        } catch (IOException e) {
            throw new AuditCollectorException("audit-collector response parse failed", e);
        }
    }
}
