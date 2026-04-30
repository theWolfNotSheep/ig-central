package co.uk.wolfnotsheep.indexing.service;

import co.uk.wolfnotsheep.document.models.DocumentModel;
import co.uk.wolfnotsheep.document.models.DocumentStatus;
import co.uk.wolfnotsheep.document.repositories.DocumentRepository;
import co.uk.wolfnotsheep.indexing.quarantine.QuarantineRecord;
import co.uk.wolfnotsheep.indexing.quarantine.QuarantineRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Lifted from the legacy in-process {@code ElasticsearchIndexService}
 * in {@code gls-app-assembly}. Same ES write logic + index template,
 * with two new behaviours required by the Phase 1.11 plan:
 *
 * <ol>
 *   <li>4xx responses (mapping conflicts, etc.) park the document in
 *       the {@code index_quarantine} Mongo collection and surface as
 *       {@link MappingConflictException} so the controller can return
 *       422 / `INDEX_MAPPING_CONFLICT` per the contract.</li>
 *   <li>5xx + transport failures surface as
 *       {@link IndexBackendUnavailableException} so the controller can
 *       return 503 / `INDEX_BACKEND_UNAVAILABLE`. Documents are not
 *       quarantined on transient failures — they should be retried.</li>
 * </ol>
 */
@Service
public class IndexingService {

    private static final Logger log = LoggerFactory.getLogger(IndexingService.class);
    public static final String INDEX_NAME = "ig_central_documents";

    private final DocumentRepository documentRepo;
    private final QuarantineRepository quarantineRepo;
    private final HttpClient httpClient;
    private final String esUri;

    public IndexingService(DocumentRepository documentRepo,
                           QuarantineRepository quarantineRepo,
                           @Value("${spring.elasticsearch.uris:http://localhost:9200}") String esUri) {
        this.documentRepo = documentRepo;
        this.quarantineRepo = quarantineRepo;
        this.esUri = esUri;
        this.httpClient = HttpClient.newHttpClient();
    }

    @PostConstruct
    public void ensureIndex() {
        try {
            HttpRequest check = HttpRequest.newBuilder()
                    .uri(URI.create(esUri + "/" + INDEX_NAME))
                    .GET().build();
            HttpResponse<String> resp = httpClient.send(check, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                log.info("Elasticsearch index '{}' already exists", INDEX_NAME);
                return;
            }

            String mappings = """
                {
                  "settings": {
                    "number_of_shards": 1,
                    "number_of_replicas": 0,
                    "analysis": {
                      "analyzer": {
                        "filename_analyzer": {
                          "type": "custom",
                          "tokenizer": "standard",
                          "filter": ["lowercase", "asciifolding"]
                        }
                      }
                    }
                  },
                  "mappings": {
                    "dynamic": true,
                    "properties": {
                      "originalFileName": { "type": "text", "analyzer": "filename_analyzer", "fields": { "keyword": { "type": "keyword" } } },
                      "status": { "type": "keyword" },
                      "categoryId": { "type": "keyword" },
                      "categoryName": { "type": "text", "fields": { "keyword": { "type": "keyword" } } },
                      "sensitivityLabel": { "type": "keyword" },
                      "tags": { "type": "keyword" },
                      "uploadedBy": { "type": "keyword" },
                      "mimeType": { "type": "keyword" },
                      "storageProvider": { "type": "keyword" },
                      "extractedText": { "type": "text" },
                      "createdAt": { "type": "date" },
                      "classifiedAt": { "type": "date" },
                      "fileSizeBytes": { "type": "long" },
                      "slug": { "type": "keyword" },
                      "extractedMetadata": { "type": "object", "dynamic": true },
                      "classificationCode": { "type": "keyword" },
                      "classificationPath": { "type": "keyword" },
                      "classificationLevel": { "type": "keyword" }
                    }
                  }
                }
                """;
            HttpRequest create = HttpRequest.newBuilder()
                    .uri(URI.create(esUri + "/" + INDEX_NAME))
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(mappings))
                    .build();
            HttpResponse<String> createResp = httpClient.send(create, HttpResponse.BodyHandlers.ofString());
            if (createResp.statusCode() == 200 || createResp.statusCode() == 201) {
                log.info("Created Elasticsearch index '{}'", INDEX_NAME);
            } else {
                log.error("Failed to create ES index: HTTP {} — {}", createResp.statusCode(), createResp.body());
            }
        } catch (Exception e) {
            log.warn("Elasticsearch not reachable at startup ({}). Index will be created on first write.", e.getMessage());
        }
    }

    /**
     * Index a single document by id. Throws on backend / mapping
     * failures — the controller maps to RFC 7807 envelopes.
     *
     * @return ES `_version` after the upsert (best-effort; 0 if not present).
     */
    public IndexOutcome indexDocument(String documentId) {
        DocumentModel doc = documentRepo.findById(documentId).orElse(null);
        if (doc == null) {
            throw new DocumentNotFoundException(documentId);
        }
        return writeDocument(doc);
    }

    private IndexOutcome writeDocument(DocumentModel doc) {
        String json = buildEsDocument(doc);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(esUri + "/" + INDEX_NAME + "/_doc/" + doc.getId()))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> resp;
        try {
            resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            throw new IndexBackendUnavailableException(
                    "ES transport failure for " + doc.getId() + ": " + e.getMessage(), e);
        }

        if (resp.statusCode() == 200 || resp.statusCode() == 201) {
            long version = parseEsVersion(resp.body());
            return new IndexOutcome(doc.getId(), INDEX_NAME, version);
        }
        if (resp.statusCode() >= 500) {
            throw new IndexBackendUnavailableException(
                    "ES " + resp.statusCode() + " for " + doc.getId() + ": " + truncate(resp.body(), 512));
        }
        // 4xx — mapping conflict or rejected payload. Park in quarantine.
        QuarantineRecord rec = new QuarantineRecord(
                doc.getId(),
                "ES rejected with HTTP " + resp.statusCode(),
                resp.statusCode(),
                truncate(resp.body(), 4096),
                truncate(json, 4096),
                Instant.now());
        quarantineRepo.save(rec);
        log.warn("ES rejected document {} with HTTP {} — parked in index_quarantine",
                doc.getId(), resp.statusCode());
        throw new MappingConflictException(doc.getId(), resp.statusCode(), resp.body());
    }

    /**
     * Remove a document from the index. Idempotent — returns
     * `NOT_FOUND` if the document wasn't present.
     */
    public DeleteOutcome removeDocument(String documentId) {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(esUri + "/" + INDEX_NAME + "/_doc/" + documentId))
                .DELETE().build();
        HttpResponse<String> resp;
        try {
            resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            throw new IndexBackendUnavailableException(
                    "ES transport failure for delete " + documentId + ": " + e.getMessage(), e);
        }
        if (resp.statusCode() == 200 || resp.statusCode() == 404) {
            // ES returns 200 with body containing "result":"deleted" / "not_found".
            boolean notFound = resp.statusCode() == 404
                    || (resp.body() != null && resp.body().contains("\"not_found\""));
            return new DeleteOutcome(documentId, notFound ? "NOT_FOUND" : "DELETED");
        }
        if (resp.statusCode() >= 500) {
            throw new IndexBackendUnavailableException(
                    "ES " + resp.statusCode() + " for delete " + documentId + ": " + truncate(resp.body(), 512));
        }
        // Treat 4xx on delete as backend trouble — don't quarantine.
        throw new IndexBackendUnavailableException(
                "ES " + resp.statusCode() + " for delete " + documentId + ": " + truncate(resp.body(), 512));
    }

    /**
     * Bulk reindex every non-disposed document. Returns aggregate
     * counts so the controller can compose a {@code ReindexSummary}.
     *
     * @param statusFilter optional whitelist of document statuses; null/empty means "all non-DISPOSED".
     */
    public ReindexSummary reindexAll(List<String> statusFilter) {
        Instant started = Instant.now();
        List<DocumentModel> docs = documentRepo.findAll();
        int total = docs.size(), indexed = 0, skipped = 0, failed = 0;
        for (DocumentModel doc : docs) {
            if (doc.getStatus() == DocumentStatus.DISPOSED) { skipped++; continue; }
            if (statusFilter != null && !statusFilter.isEmpty()) {
                String name = doc.getStatus() == null ? null : doc.getStatus().name();
                if (name == null || !statusFilter.contains(name)) { skipped++; continue; }
            }
            try {
                writeDocument(doc);
                indexed++;
            } catch (RuntimeException e) {
                log.warn("Reindex failed for {}: {}", doc.getId(), e.getMessage());
                failed++;
            }
        }
        long durationMs = Math.max(0L, java.time.Duration.between(started, Instant.now()).toMillis());
        log.info("Reindex complete: {} indexed, {} skipped, {} failed (total {})",
                indexed, skipped, failed, total);
        return new ReindexSummary(total, indexed, skipped, failed, durationMs);
    }

    static String buildEsDocument(DocumentModel doc) {
        StringBuilder sb = new StringBuilder("{");
        appendField(sb, "originalFileName", doc.getOriginalFileName());
        appendField(sb, "status", doc.getStatus() != null ? doc.getStatus().name() : null);
        appendField(sb, "categoryId", doc.getCategoryId());
        appendField(sb, "categoryName", doc.getCategoryName());
        appendField(sb, "sensitivityLabel", doc.getSensitivityLabel() != null ? doc.getSensitivityLabel().name() : null);
        appendField(sb, "uploadedBy", doc.getUploadedBy());
        appendField(sb, "mimeType", doc.getMimeType());
        appendField(sb, "storageProvider", doc.getStorageProvider());
        appendField(sb, "slug", doc.getSlug());
        appendField(sb, "createdAt", doc.getCreatedAt() != null ? doc.getCreatedAt().toString() : null);
        appendField(sb, "classifiedAt", doc.getClassifiedAt() != null ? doc.getClassifiedAt().toString() : null);
        sb.append("\"fileSizeBytes\":").append(doc.getFileSizeBytes()).append(",");

        if (doc.getTags() != null && !doc.getTags().isEmpty()) {
            sb.append("\"tags\":[");
            for (int i = 0; i < doc.getTags().size(); i++) {
                if (i > 0) sb.append(",");
                sb.append("\"").append(escapeJson(doc.getTags().get(i))).append("\"");
            }
            sb.append("],");
        }

        appendField(sb, "classificationCode", doc.getClassificationCode());
        appendField(sb, "classificationLevel",
                doc.getClassificationLevel() != null ? doc.getClassificationLevel().name() : null);
        if (doc.getClassificationPath() != null && !doc.getClassificationPath().isEmpty()) {
            sb.append("\"classificationPath\":[");
            for (int i = 0; i < doc.getClassificationPath().size(); i++) {
                if (i > 0) sb.append(",");
                sb.append("\"").append(escapeJson(doc.getClassificationPath().get(i))).append("\"");
            }
            sb.append("],");
        }

        String text = doc.getExtractedText();
        if (text != null) {
            if (text.length() > 50000) text = text.substring(0, 50000);
            appendField(sb, "extractedText", text);
        }

        if (doc.getExtractedMetadata() != null && !doc.getExtractedMetadata().isEmpty()) {
            sb.append("\"extractedMetadata\":{");
            boolean first = true;
            for (var entry : doc.getExtractedMetadata().entrySet()) {
                if (!first) sb.append(",");
                sb.append("\"").append(escapeJson(entry.getKey())).append("\":\"")
                        .append(escapeJson(entry.getValue())).append("\"");
                first = false;
            }
            sb.append("},");
        }

        if (sb.charAt(sb.length() - 1) == ',') sb.setLength(sb.length() - 1);
        sb.append("}");
        return sb.toString();
    }

    private static void appendField(StringBuilder sb, String key, String value) {
        if (value == null) return;
        sb.append("\"").append(key).append("\":\"").append(escapeJson(value)).append("\",");
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    /** Best-effort `_version` parser without pulling in a JSON dep just for this. */
    private static long parseEsVersion(String body) {
        if (body == null) return 0L;
        int idx = body.indexOf("\"_version\":");
        if (idx < 0) return 0L;
        int start = idx + 11;
        int end = start;
        while (end < body.length() && Character.isDigit(body.charAt(end))) end++;
        if (end == start) return 0L;
        try { return Long.parseLong(body.substring(start, end)); }
        catch (NumberFormatException e) { return 0L; }
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    public record IndexOutcome(String documentId, String indexName, long version) {}

    public record DeleteOutcome(String documentId, String result) {}

    public record ReindexSummary(int totalDocuments, int indexedCount,
                                 int skippedCount, int failedCount, long durationMs) {}
}
