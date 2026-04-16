package co.uk.wolfnotsheep.infrastructure.services;

import co.uk.wolfnotsheep.document.models.DocumentModel;
import co.uk.wolfnotsheep.document.models.DocumentStatus;
import co.uk.wolfnotsheep.document.repositories.DocumentRepository;
import co.uk.wolfnotsheep.document.services.ObjectStorageService;
import co.uk.wolfnotsheep.platform.config.services.AppConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.bson.Document;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;

@Service
public class MonitoringService {

    private static final Logger log = LoggerFactory.getLogger(MonitoringService.class);

    private final DocumentRepository documentRepo;
    private final MongoTemplate mongoTemplate;
    private final ObjectStorageService objectStorage;
    private final AppConfigService configService;
    private final HttpClient httpClient;
    private final List<ServiceDef> services;
    private final String rabbitmqManagementUrl;
    private final String rabbitmqUser;
    private final String rabbitmqPassword;

    public record ServiceDef(String name, String url) {}

    public MonitoringService(
            DocumentRepository documentRepo,
            MongoTemplate mongoTemplate,
            ObjectStorageService objectStorage,
            AppConfigService configService,
            @Value("${monitoring.rabbitmq-management-url:http://localhost:15672/api}") String rabbitmqManagementUrl,
            @Value("${spring.rabbitmq.username:guest}") String rabbitmqUser,
            @Value("${spring.rabbitmq.password:guest}") String rabbitmqPassword,
            @Value("${monitoring.health-check-timeout-ms:3000}") int timeoutMs) {
        this.documentRepo = documentRepo;
        this.mongoTemplate = mongoTemplate;
        this.objectStorage = objectStorage;
        this.configService = configService;
        this.rabbitmqManagementUrl = rabbitmqManagementUrl;
        this.rabbitmqUser = rabbitmqUser;
        this.rabbitmqPassword = rabbitmqPassword;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .build();
        this.services = new ArrayList<>();
    }

    @Value("${monitoring.services[0].name:api}")
    private String s0Name;
    @Value("${monitoring.services[0].url:http://localhost:8080/actuator/health}")
    private String s0Url;
    @Value("${monitoring.services[1].name:mcp-server}")
    private String s1Name;
    @Value("${monitoring.services[1].url:http://localhost:8081/actuator/health}")
    private String s1Url;
    @Value("${monitoring.services[2].name:llm-worker}")
    private String s2Name;
    @Value("${monitoring.services[2].url:http://localhost:8082/actuator/health}")
    private String s2Url;
    @Value("${monitoring.services[3].name:bert-classifier}")
    private String s3Name;
    @Value("${monitoring.services[3].url:http://bert-classifier:8000/health}")
    private String s3Url;

    @jakarta.annotation.PostConstruct
    void init() {
        services.addAll(List.of(
                new ServiceDef(s0Name, s0Url), new ServiceDef(s1Name, s1Url),
                new ServiceDef(s2Name, s2Url), new ServiceDef(s3Name, s3Url)));
    }

    // ── Service Health ────────────────────────────────────

    public Map<String, Object> getServiceHealth() {
        List<CompletableFuture<Map<String, Object>>> futures = services.stream()
                .map(svc -> CompletableFuture.supplyAsync(() -> checkHealth(svc)))
                .toList();

        List<Map<String, Object>> results = new ArrayList<>(futures.stream()
                .map(f -> { try { return f.get(5, TimeUnit.SECONDS); } catch (Exception e) { return Map.<String, Object>of("name", "unknown", "status", "TIMEOUT"); } })
                .toList());

        // MinIO health check (not HTTP-based, uses S3 API)
        long minioStart = System.currentTimeMillis();
        boolean minioHealthy = objectStorage.healthCheck();
        long minioElapsed = System.currentTimeMillis() - minioStart;
        results.add(minioHealthy
                ? Map.of("name", "minio", "status", "UP", "responseTimeMs", minioElapsed)
                : Map.of("name", "minio", "status", "DOWN", "responseTimeMs", minioElapsed, "error", "Bucket check failed"));

        // Ollama health check (runs on host, not a Docker service)
        String ollamaUrl = getOllamaBaseUrl();
        results.add(checkHealth(new ServiceDef("ollama", ollamaUrl + "/api/tags")));

        return Map.of("services", results, "timestamp", Instant.now().toString());
    }

    private Map<String, Object> checkHealth(ServiceDef svc) {
        long start = System.currentTimeMillis();
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(svc.url()))
                    .timeout(Duration.ofSeconds(3))
                    .GET().build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            long elapsed = System.currentTimeMillis() - start;
            String status = resp.statusCode() == 200 ? "UP" : "DEGRADED";
            return Map.of("name", svc.name(), "status", status, "responseTimeMs", elapsed);
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            return Map.of("name", svc.name(), "status", "DOWN", "responseTimeMs", elapsed, "error", e.getMessage() != null ? e.getMessage() : "Connection failed");
        }
    }

    public Map<String, Object> pingService(String serviceName) {
        if ("ollama".equals(serviceName)) {
            return checkHealth(new ServiceDef("ollama", getOllamaBaseUrl() + "/api/tags"));
        }
        return services.stream()
                .filter(s -> s.name().equals(serviceName))
                .findFirst()
                .map(this::checkHealth)
                .orElse(Map.of("name", serviceName, "status", "UNKNOWN", "error", "Service not configured"));
    }

    private String getOllamaBaseUrl() {
        String envUrl = System.getenv("OLLAMA_BASE_URL");
        String fallback = envUrl != null ? envUrl : "http://localhost:11434";
        return configService.getValue("llm.ollama.base_url", fallback);
    }

    // ── Pipeline Metrics ──────────────────────────────────

    public Map<String, Object> getPipelineMetrics() {
        Map<String, Long> statusCounts = new LinkedHashMap<>();
        for (DocumentStatus s : DocumentStatus.values()) {
            statusCounts.put(s.name(), documentRepo.countByStatus(s));
        }

        long totalDocs = statusCounts.values().stream().mapToLong(Long::longValue).sum();

        // Throughput
        Instant now = Instant.now();
        long last24h = mongoTemplate.count(
                Query.query(Criteria.where("classifiedAt").gte(now.minus(24, ChronoUnit.HOURS))),
                DocumentModel.class);
        long last7d = mongoTemplate.count(
                Query.query(Criteria.where("classifiedAt").gte(now.minus(7, ChronoUnit.DAYS))),
                DocumentModel.class);

        // Average processing times — project only the two timestamp fields, not full docs
        Query classifyQuery = Query.query(Criteria.where("classifiedAt").ne(null).and("createdAt").ne(null))
                .limit(100);
        classifyQuery.fields().include("createdAt").include("classifiedAt");
        List<DocumentModel> classified = mongoTemplate.find(classifyQuery, DocumentModel.class);

        long avgClassifyMs = 0;
        if (!classified.isEmpty()) {
            avgClassifyMs = (long) classified.stream()
                    .mapToLong(d -> Duration.between(d.getCreatedAt(), d.getClassifiedAt()).toMillis())
                    .average().orElse(0);
        }

        // Stale documents (stuck in processing > 10 min)
        long stale = mongoTemplate.count(
                Query.query(Criteria.where("status").in("PROCESSING", "CLASSIFYING")
                        .and("updatedAt").lt(now.minus(10, ChronoUnit.MINUTES))),
                DocumentModel.class);

        // Queue depths
        Map<String, Object> queues = getQueueDepths();

        return Map.of(
                "statusCounts", statusCounts,
                "totalDocuments", totalDocs,
                "throughput", Map.of("last24h", last24h, "last7d", last7d),
                "avgClassificationTimeMs", avgClassifyMs,
                "staleDocuments", stale,
                "queueDepths", queues
        );
    }

    // ── Infrastructure Stats ──────────────────────────────

    public Map<String, Object> getInfrastructureStats() {
        // MongoDB stats
        Map<String, Object> mongoStats = new LinkedHashMap<>();
        try {
            Document dbStats = mongoTemplate.executeCommand(new Document("dbStats", 1));
            mongoStats.put("databaseSizeBytes", dbStats.get("dataSize"));
            mongoStats.put("storageSizeBytes", dbStats.get("storageSize"));
            mongoStats.put("collections", dbStats.get("collections"));
            mongoStats.put("totalObjects", dbStats.get("objects"));
        } catch (Exception e) {
            mongoStats.put("error", e.getMessage());
        }

        // Storage totals — use aggregation instead of loading all documents
        long totalFiles = documentRepo.count();
        long totalSizeBytes = 0;
        try {
            var pipeline = org.springframework.data.mongodb.core.aggregation.Aggregation.newAggregation(
                    org.springframework.data.mongodb.core.aggregation.Aggregation.group().sum("fileSizeBytes").as("total")
            );
            var result = mongoTemplate.aggregate(pipeline, "documents", org.bson.Document.class);
            var first = result.getUniqueMappedResult();
            if (first != null) totalSizeBytes = first.getLong("total");
        } catch (Exception e) {
            log.warn("Failed to aggregate storage size: {}", e.getMessage());
        }

        Map<String, Object> storage = Map.of(
                "totalFiles", totalFiles,
                "totalSizeBytes", totalSizeBytes
        );

        // RabbitMQ overview
        Map<String, Object> rabbitmq = getRabbitOverview();

        return Map.of("mongodb", mongoStats, "storage", storage, "rabbitmq", rabbitmq);
    }

    // ── RabbitMQ Helpers ──────────────────────────────────

    private Map<String, Object> getQueueDepths() {
        String[] queueNames = {
                "gls.documents.ingested", "gls.documents.processed",
                "gls.documents.classified", "gls.documents.classification.failed"
        };
        Map<String, Object> depths = new LinkedHashMap<>();
        for (String q : queueNames) {
            try {
                String body = rabbitGet("/queues/%2F/" + q);
                // Simple JSON parsing for messages field
                int messages = extractJsonInt(body, "messages");
                depths.put(q, messages);
            } catch (Exception e) {
                depths.put(q, -1);
            }
        }
        return depths;
    }

    private Map<String, Object> getRabbitOverview() {
        try {
            String body = rabbitGet("/overview");
            int totalQueues = extractJsonInt(body, "queue_totals");
            return Map.of("status", "UP", "overview", "connected");
        } catch (Exception e) {
            return Map.of("status", "DOWN", "error", e.getMessage() != null ? e.getMessage() : "Connection failed");
        }
    }

    private String rabbitGet(String path) throws Exception {
        String auth = Base64.getEncoder().encodeToString((rabbitmqUser + ":" + rabbitmqPassword).getBytes());
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(rabbitmqManagementUrl + path))
                .header("Authorization", "Basic " + auth)
                .timeout(Duration.ofSeconds(3))
                .GET().build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) throw new RuntimeException("HTTP " + resp.statusCode());
        return resp.body();
    }

    private int extractJsonInt(String json, String key) {
        try {
            int idx = json.indexOf("\"" + key + "\"");
            if (idx < 0) return 0;
            int colon = json.indexOf(":", idx);
            int end = json.indexOf(",", colon);
            if (end < 0) end = json.indexOf("}", colon);
            return Integer.parseInt(json.substring(colon + 1, end).trim());
        } catch (Exception e) {
            return 0;
        }
    }
}
