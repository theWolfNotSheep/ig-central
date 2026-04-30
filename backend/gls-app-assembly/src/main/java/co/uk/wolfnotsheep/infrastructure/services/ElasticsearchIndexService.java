package co.uk.wolfnotsheep.infrastructure.services;

import co.uk.wolfnotsheep.document.models.DocumentModel;
import co.uk.wolfnotsheep.document.models.DocumentStatus;
import co.uk.wolfnotsheep.document.models.SystemError;
import co.uk.wolfnotsheep.document.repositories.DocumentRepository;
import co.uk.wolfnotsheep.document.repositories.SystemErrorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

/**
 * Indexes documents into Elasticsearch for fast full-text search.
 * MongoDB remains the system of record — ES is the search index.
 *
 * <p>Phase 1.11 PR3 adds an opt-in {@link IndexingWorkerClient}
 * delegate. When the bean is present (i.e.
 * {@code pipeline.indexing-worker.enabled=true}), each public
 * write method dispatches to the worker over HTTP instead of doing
 * the work in-process. When absent (default), the legacy in-process
 * ES write logic runs unchanged. The worker container reuses the
 * same {@code IndexingService} internally — this is a transport
 * swap, not a behavioural change.
 */
@Service
public class ElasticsearchIndexService {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchIndexService.class);
    private static final String INDEX_NAME = "ig_central_documents";

    private final DocumentRepository documentRepo;
    private final SystemErrorRepository systemErrorRepo;
    private final HttpClient httpClient;
    private final ObjectProvider<IndexingWorkerClient> workerClientProvider;

    @org.springframework.beans.factory.annotation.Value("${spring.elasticsearch.uris:http://localhost:9200}")
    private String esUri;

    public ElasticsearchIndexService(DocumentRepository documentRepo,
                                     SystemErrorRepository systemErrorRepo,
                                     ObjectProvider<IndexingWorkerClient> workerClientProvider) {
        this.documentRepo = documentRepo;
        this.systemErrorRepo = systemErrorRepo;
        this.workerClientProvider = workerClientProvider;
        this.httpClient = HttpClient.newHttpClient();
    }

    /**
     * Ensure the index exists with the right mappings.
     */
    @jakarta.annotation.PostConstruct
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

            // Create index with mappings
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
                log.error("Failed to create ES index: {}", createResp.body());
            }
        } catch (Exception e) {
            log.warn("Elasticsearch not available — search will use MongoDB fallback: {}", e.getMessage());
        }
    }

    /**
     * Index a single document. Called after every status change.
     * When the worker bean is present, dispatches to it over HTTP
     * instead of writing to ES in-process.
     */
    @Async
    public void indexDocument(String documentId) {
        IndexingWorkerClient worker = workerClientProvider.getIfAvailable();
        if (worker != null) {
            try {
                long version = worker.indexDocument(documentId);
                log.debug("indexed via worker doc={} version={}", documentId, version);
            } catch (RuntimeException e) {
                log.error("indexing-worker index failed for {}: {}", documentId, e.getMessage());
                persistEsError(documentId, "indexing-worker exception: " + e.getMessage());
            }
            return;
        }
        try {
            DocumentModel doc = documentRepo.findById(documentId).orElse(null);
            if (doc == null) return;

            // Build the ES document — only index searchable fields, not the full extracted text for small docs
            String json = buildEsDocument(doc);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(esUri + "/" + INDEX_NAME + "/_doc/" + documentId))
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200 && resp.statusCode() != 201) {
                log.error("ES index failed for {}: HTTP {} — {}", documentId, resp.statusCode(), resp.body());
                persistEsError(documentId, "ES index failed (HTTP " + resp.statusCode() + ")");
            }
        } catch (Exception e) {
            log.error("ES indexing failed for {}: {}", documentId, e.getMessage());
            persistEsError(documentId, "ES indexing exception: " + e.getMessage());
        }
    }

    private void persistEsError(String documentId, String message) {
        try {
            SystemError error = SystemError.of("ERROR", "INTERNAL", message);
            error.setDocumentId(documentId);
            error.setService("api");
            systemErrorRepo.save(error);
        } catch (Exception ex) {
            log.warn("Failed to persist ES error to SystemError: {}", ex.getMessage());
        }
    }

    /**
     * Remove a document from the index. When the worker bean is
     * present, dispatches to it over HTTP.
     */
    public void removeDocument(String documentId) {
        IndexingWorkerClient worker = workerClientProvider.getIfAvailable();
        if (worker != null) {
            try {
                String result = worker.removeDocument(documentId);
                log.debug("removed via worker doc={} result={}", documentId, result);
            } catch (RuntimeException e) {
                log.warn("indexing-worker delete failed for {}: {}", documentId, e.getMessage());
            }
            return;
        }
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(esUri + "/" + INDEX_NAME + "/_doc/" + documentId))
                    .DELETE().build();
            httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            log.warn("ES delete failed for {}: {}", documentId, e.getMessage());
        }
    }

    /**
     * Bulk reindex all documents. Admin-triggered. When the worker
     * bean is present, fires a dispatch and returns -1 to signal
     * "in-flight" — the legacy synchronous int-count semantics don't
     * fit the worker's async surface; admins poll the monitoring
     * page for completion. Returns 0 on dispatch failure.
     */
    public int reindexAll() {
        IndexingWorkerClient worker = workerClientProvider.getIfAvailable();
        if (worker != null) {
            try {
                String nodeRunId = worker.reindex();
                log.info("dispatched reindex to worker (nodeRunId={})", nodeRunId);
                return -1;
            } catch (RuntimeException e) {
                log.error("indexing-worker reindex dispatch failed: {}", e.getMessage());
                return 0;
            }
        }
        List<DocumentModel> docs = documentRepo.findAll();
        int indexed = 0;
        for (DocumentModel doc : docs) {
            if (doc.getStatus() == DocumentStatus.DISPOSED) continue;
            try {
                String json = buildEsDocument(doc);
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(esUri + "/" + INDEX_NAME + "/_doc/" + doc.getId()))
                        .header("Content-Type", "application/json")
                        .PUT(HttpRequest.BodyPublishers.ofString(json))
                        .build();
                httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                indexed++;
            } catch (Exception e) {
                log.warn("Reindex failed for {}: {}", doc.getId(), e.getMessage());
            }
        }
        log.info("Reindexed {} documents into Elasticsearch", indexed);
        return indexed;
    }

    /**
     * Search ES and return document IDs.
     */
    public List<String> search(String queryJson) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(esUri + "/" + INDEX_NAME + "/_search"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(queryJson))
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) return List.of();

            // Extract document IDs from hits
            String body = resp.body();
            List<String> ids = new java.util.ArrayList<>();
            int idx = 0;
            while ((idx = body.indexOf("\"_id\":\"", idx)) >= 0) {
                int start = idx + 7;
                int end = body.indexOf("\"", start);
                if (end > start) ids.add(body.substring(start, end));
                idx = end;
            }
            return ids;
        } catch (Exception e) {
            log.error("ES search failed: {}", e.getMessage());
            return List.of();
        }
    }

    private String buildEsDocument(DocumentModel doc) {
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

        // Tags
        if (doc.getTags() != null && !doc.getTags().isEmpty()) {
            sb.append("\"tags\":[");
            for (int i = 0; i < doc.getTags().size(); i++) {
                if (i > 0) sb.append(",");
                sb.append("\"").append(escapeJson(doc.getTags().get(i))).append("\"");
            }
            sb.append("],");
        }

        // Classification codes (ISO 15489)
        appendField(sb, "classificationCode", doc.getClassificationCode());
        appendField(sb, "classificationLevel", doc.getClassificationLevel() != null ? doc.getClassificationLevel().name() : null);
        if (doc.getClassificationPath() != null && !doc.getClassificationPath().isEmpty()) {
            sb.append("\"classificationPath\":[");
            for (int i = 0; i < doc.getClassificationPath().size(); i++) {
                if (i > 0) sb.append(",");
                sb.append("\"").append(escapeJson(doc.getClassificationPath().get(i))).append("\"");
            }
            sb.append("],");
        }

        // Extracted text (truncate for ES — full text stays in MongoDB)
        String text = doc.getExtractedText();
        if (text != null) {
            if (text.length() > 50000) text = text.substring(0, 50000);
            appendField(sb, "extractedText", text);
        }

        // Extracted metadata as dynamic fields
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

        // Remove trailing comma and close
        if (sb.charAt(sb.length() - 1) == ',') sb.setLength(sb.length() - 1);
        sb.append("}");
        return sb.toString();
    }

    private void appendField(StringBuilder sb, String key, String value) {
        if (value == null) return;
        sb.append("\"").append(key).append("\":\"").append(escapeJson(value)).append("\",");
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}
