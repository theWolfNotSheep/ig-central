package co.uk.wolfnotsheep.auditcollector.store;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessResourceFailureException;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Elasticsearch-backed {@link Tier2Store}. Activated by
 * {@code igc.audit.collector.tier2-backend=es}.
 *
 * <p>Mirrors the {@code igc-indexing-worker}'s `IndexingService`
 * approach — JDK {@code HttpClient} + manual JSON. We keep the
 * footprint small (no spring-data-elasticsearch dep) since the
 * collector talks to ES via a stable handful of endpoints.
 *
 * <p>Index name {@code audit_tier2_events}. Mappings are created on
 * first startup if missing — deliberately permissive (`dynamic: true`)
 * so the {@code envelope} object survives schema drift; we keep
 * explicit mappings on the denormalised filter fields so search
 * stays deterministic.
 */
@Service
@ConditionalOnProperty(name = "igc.audit.collector.tier2-backend", havingValue = "es")
public class EsTier2Store implements Tier2Store {

    private static final Logger log = LoggerFactory.getLogger(EsTier2Store.class);

    static final String INDEX_NAME = "audit_tier2_events";

    private final HttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper();
    private final String esUri;
    private final Duration timeout;

    public EsTier2Store(
            @Value("${spring.elasticsearch.uris:http://localhost:9200}") String esUri,
            @Value("${igc.audit.collector.tier2-backend.timeout-ms:30000}") int timeoutMs) {
        this.esUri = stripTrailing(esUri);
        this.timeout = Duration.ofMillis(timeoutMs);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @PostConstruct
    public void ensureIndex() {
        try {
            HttpRequest check = HttpRequest.newBuilder()
                    .uri(URI.create(esUri + "/" + INDEX_NAME))
                    .GET().timeout(timeout).build();
            HttpResponse<String> resp = httpClient.send(check, BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                log.info("ES audit index '{}' already exists", INDEX_NAME);
                return;
            }
            String mappings = """
                {
                  "settings": {
                    "number_of_shards": 1,
                    "number_of_replicas": 0
                  },
                  "mappings": {
                    "dynamic": true,
                    "properties": {
                      "eventId":         { "type": "keyword" },
                      "eventType":       { "type": "keyword" },
                      "tier":            { "type": "keyword" },
                      "schemaVersion":   { "type": "keyword" },
                      "timestamp":       { "type": "date" },
                      "documentId":      { "type": "keyword" },
                      "pipelineRunId":   { "type": "keyword" },
                      "nodeRunId":       { "type": "keyword" },
                      "traceparent":     { "type": "keyword" },
                      "actorService":    { "type": "keyword" },
                      "actorType":       { "type": "keyword" },
                      "resourceType":    { "type": "keyword" },
                      "resourceId":      { "type": "keyword" },
                      "action":          { "type": "keyword" },
                      "outcome":         { "type": "keyword" },
                      "retentionClass":  { "type": "keyword" },
                      "envelope":        { "type": "object", "dynamic": true }
                    }
                  }
                }
                """;
            HttpRequest create = HttpRequest.newBuilder()
                    .uri(URI.create(esUri + "/" + INDEX_NAME))
                    .header("Content-Type", "application/json")
                    .PUT(BodyPublishers.ofString(mappings))
                    .timeout(timeout).build();
            HttpResponse<String> createResp = httpClient.send(create, BodyHandlers.ofString());
            if (createResp.statusCode() == 200 || createResp.statusCode() == 201) {
                log.info("Created ES audit index '{}'", INDEX_NAME);
            } else {
                log.error("Failed to create ES audit index: HTTP {} — {}",
                        createResp.statusCode(), createResp.body());
            }
        } catch (Exception e) {
            log.warn("ES not reachable at startup ({}). Index will be created on first write.",
                    e.getMessage());
        }
    }

    @Override
    public void save(StoredTier2Event event) {
        try {
            String body = mapper.writeValueAsString(toJsonMap(event));
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(esUri + "/" + INDEX_NAME + "/_doc/" + event.getEventId()))
                    .header("Content-Type", "application/json")
                    .PUT(BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .timeout(timeout).build();
            HttpResponse<String> resp = httpClient.send(req, BodyHandlers.ofString());
            if (resp.statusCode() != 200 && resp.statusCode() != 201) {
                throw new DataAccessResourceFailureException(
                        "ES tier2 save HTTP " + resp.statusCode() + " for " + event.getEventId());
            }
        } catch (IOException e) {
            throw new UncheckedIOException("ES tier2 save serialise/transport failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DataAccessResourceFailureException("ES tier2 save interrupted", e);
        }
    }

    @Override
    public Optional<StoredTier2Event> findById(String eventId) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(esUri + "/" + INDEX_NAME + "/_doc/" + eventId))
                    .GET().timeout(timeout).build();
            HttpResponse<String> resp = httpClient.send(req, BodyHandlers.ofString());
            if (resp.statusCode() == 404) return Optional.empty();
            if (resp.statusCode() != 200) {
                throw new DataAccessResourceFailureException(
                        "ES tier2 fetch HTTP " + resp.statusCode() + " for " + eventId);
            }
            JsonNode root = mapper.readTree(resp.body());
            JsonNode source = root.path("_source");
            if (source.isMissingNode() || source.isNull()) return Optional.empty();
            return Optional.of(toRow(source));
        } catch (IOException e) {
            throw new UncheckedIOException("ES tier2 fetch parse failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DataAccessResourceFailureException("ES tier2 fetch interrupted", e);
        }
    }

    @Override
    public List<StoredTier2Event> search(SearchCriteria criteria, int pageIndex, int pageSize) {
        try {
            Map<String, Object> body = buildSearchBody(criteria, pageIndex, pageSize);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(esUri + "/" + INDEX_NAME + "/_search"))
                    .header("Content-Type", "application/json")
                    .POST(BodyPublishers.ofString(mapper.writeValueAsString(body), StandardCharsets.UTF_8))
                    .timeout(timeout).build();
            HttpResponse<String> resp = httpClient.send(req, BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                throw new DataAccessResourceFailureException(
                        "ES tier2 search HTTP " + resp.statusCode());
            }
            return parseHits(resp.body());
        } catch (IOException e) {
            throw new UncheckedIOException("ES tier2 search parse failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DataAccessResourceFailureException("ES tier2 search interrupted", e);
        }
    }

    private Map<String, Object> buildSearchBody(SearchCriteria criteria, int pageIndex, int pageSize) {
        List<Map<String, Object>> filters = new ArrayList<>();
        if (criteria.documentId() != null) {
            filters.add(Map.of("term", Map.of("documentId", criteria.documentId())));
        }
        if (criteria.eventType() != null) {
            filters.add(Map.of("term", Map.of("eventType", criteria.eventType())));
        }
        if (criteria.actorService() != null) {
            filters.add(Map.of("term", Map.of("actorService", criteria.actorService())));
        }
        if (criteria.fromInclusive() != null || criteria.toExclusive() != null) {
            Map<String, Object> range = new LinkedHashMap<>();
            if (criteria.fromInclusive() != null) range.put("gte", criteria.fromInclusive().toString());
            if (criteria.toExclusive() != null) range.put("lt", criteria.toExclusive().toString());
            filters.add(Map.of("range", Map.of("timestamp", range)));
        }
        Map<String, Object> query = filters.isEmpty()
                ? Map.of("match_all", Map.of())
                : Map.of("bool", Map.of("filter", filters));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("query", query);
        body.put("sort", List.of(Map.of("timestamp", Map.of("order", "desc"))));
        body.put("from", pageIndex * pageSize);
        body.put("size", pageSize);
        return body;
    }

    private List<StoredTier2Event> parseHits(String json) throws JsonProcessingException {
        JsonNode root = mapper.readTree(json);
        JsonNode hits = root.path("hits").path("hits");
        if (!hits.isArray()) return List.of();
        List<StoredTier2Event> out = new ArrayList<>(hits.size());
        for (JsonNode hit : hits) {
            JsonNode source = hit.path("_source");
            if (!source.isMissingNode() && !source.isNull()) {
                out.add(toRow(source));
            }
        }
        return out;
    }

    private StoredTier2Event toRow(JsonNode source) {
        Map<String, Object> envelope = source.has("envelope")
                ? mapper.convertValue(source.get("envelope"), new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {})
                : null;
        Instant timestamp = parseInstant(source.path("timestamp").asText(null));
        return new StoredTier2Event(
                source.path("eventId").asText(null),
                source.path("eventType").asText(null),
                source.path("schemaVersion").asText(null),
                timestamp,
                source.path("documentId").asText(null),
                source.path("pipelineRunId").asText(null),
                source.path("nodeRunId").asText(null),
                source.path("traceparent").asText(null),
                source.path("actorService").asText(null),
                source.path("actorType").asText(null),
                source.path("resourceType").asText(null),
                source.path("resourceId").asText(null),
                source.path("action").asText(null),
                source.path("outcome").asText(null),
                source.path("retentionClass").asText(null),
                envelope);
    }

    private Map<String, Object> toJsonMap(StoredTier2Event event) {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("eventId", event.getEventId());
        doc.put("eventType", event.getEventType());
        doc.put("tier", event.getTier());
        doc.put("schemaVersion", event.getSchemaVersion());
        if (event.getTimestamp() != null) doc.put("timestamp", event.getTimestamp().toString());
        doc.put("documentId", event.getDocumentId());
        doc.put("pipelineRunId", event.getPipelineRunId());
        doc.put("nodeRunId", event.getNodeRunId());
        doc.put("traceparent", event.getTraceparent());
        doc.put("actorService", event.getActorService());
        doc.put("actorType", event.getActorType());
        doc.put("resourceType", event.getResourceType());
        doc.put("resourceId", event.getResourceId());
        doc.put("action", event.getAction());
        doc.put("outcome", event.getOutcome());
        doc.put("retentionClass", event.getRetentionClass());
        if (event.getEnvelope() != null) doc.put("envelope", event.getEnvelope());
        // Strip nulls to keep ES docs lean and avoid mapper "null" inference.
        Iterator<Map.Entry<String, Object>> it = doc.entrySet().iterator();
        while (it.hasNext()) if (it.next().getValue() == null) it.remove();
        return doc;
    }

    private static Instant parseInstant(String s) {
        if (s == null) return null;
        try { return Instant.parse(s); } catch (Exception e) { return null; }
    }

    private static String stripTrailing(String url) {
        if (url == null) return "";
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
