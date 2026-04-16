package co.uk.wolfnotsheep.infrastructure.controllers.admin;

import co.uk.wolfnotsheep.document.models.DocumentModel;
import co.uk.wolfnotsheep.document.services.DocumentService;
import co.uk.wolfnotsheep.document.services.PiiPatternScanner;
import co.uk.wolfnotsheep.document.models.PiiEntity;
import co.uk.wolfnotsheep.governance.models.PipelineDefinition;
import co.uk.wolfnotsheep.governance.models.NodeTypeDefinition;
import co.uk.wolfnotsheep.governance.repositories.PipelineDefinitionRepository;
import co.uk.wolfnotsheep.governance.services.NodeTypeDefinitionService;
import co.uk.wolfnotsheep.governance.services.PipelineRoutingService;
import co.uk.wolfnotsheep.governance.services.PipelineRoutingService.PipelineOverlap;
import co.uk.wolfnotsheep.infrastructure.services.pipeline.accelerators.AcceleratorHandler;
import co.uk.wolfnotsheep.infrastructure.services.pipeline.accelerators.AcceleratorResult;
import co.uk.wolfnotsheep.infrastructure.services.pipeline.GenericHttpNodeExecutor;
import co.uk.wolfnotsheep.infrastructure.services.pipeline.PipelineNodeHandlerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/admin/pipelines")
public class PipelineAdminController {

    private static final Logger log = LoggerFactory.getLogger(PipelineAdminController.class);

    private final PipelineDefinitionRepository pipelineRepo;
    private final DocumentService documentService;
    private final PiiPatternScanner piiScanner;
    private final PipelineRoutingService routingService;
    private final PipelineNodeHandlerRegistry handlerRegistry;
    private final NodeTypeDefinitionService nodeTypeService;

    public PipelineAdminController(PipelineDefinitionRepository pipelineRepo,
                                    DocumentService documentService,
                                    PiiPatternScanner piiScanner,
                                    PipelineRoutingService routingService,
                                    PipelineNodeHandlerRegistry handlerRegistry,
                                    NodeTypeDefinitionService nodeTypeService) {
        this.pipelineRepo = pipelineRepo;
        this.documentService = documentService;
        this.piiScanner = piiScanner;
        this.routingService = routingService;
        this.handlerRegistry = handlerRegistry;
        this.nodeTypeService = nodeTypeService;
    }

    @GetMapping
    public ResponseEntity<List<PipelineDefinition>> list() {
        return ResponseEntity.ok(pipelineRepo.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<PipelineDefinition> get(@PathVariable String id) {
        return pipelineRepo.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<PipelineDefinition> create(@RequestBody PipelineDefinition pipeline) {
        pipeline.setCreatedAt(Instant.now());
        pipeline.setUpdatedAt(Instant.now());
        return ResponseEntity.ok(pipelineRepo.save(pipeline));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PipelineDefinition> update(
            @PathVariable String id, @RequestBody PipelineDefinition updates) {
        return pipelineRepo.findById(id)
                .map(existing -> {
                    existing.setName(updates.getName());
                    existing.setDescription(updates.getDescription());
                    existing.setActive(updates.isActive());
                    existing.setSteps(updates.getSteps());
                    existing.setVisualNodes(updates.getVisualNodes());
                    existing.setVisualEdges(updates.getVisualEdges());
                    existing.setApplicableCategoryIds(updates.getApplicableCategoryIds());
                    existing.setIncludeSubCategories(updates.isIncludeSubCategories());
                    existing.setApplicableMimeTypes(updates.getApplicableMimeTypes());
                    existing.setDefault(updates.isDefault());
                    existing.setUpdatedAt(Instant.now());
                    return ResponseEntity.ok(pipelineRepo.save(existing));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        return pipelineRepo.findById(id)
                .map(p -> { p.setActive(false); p.setUpdatedAt(Instant.now()); pipelineRepo.save(p); return ResponseEntity.ok().<Void>build(); })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Trigger a batch PII scan on all documents that haven't been scanned yet.
     */
    @PostMapping("/pii-scan/batch")
    public ResponseEntity<Map<String, Object>> batchPiiScan() {
        List<DocumentModel> docs = documentService.getAll();
        int scanned = 0;
        int piiFound = 0;

        for (DocumentModel doc : docs) {
            if (doc.getExtractedText() == null || doc.getExtractedText().isBlank()) continue;

            List<PiiEntity> findings = piiScanner.scan(doc.getExtractedText());
            doc.setPiiFindings(findings);
            doc.setPiiScannedAt(Instant.now());
            documentService.save(doc);

            scanned++;
            piiFound += findings.size();
        }

        return ResponseEntity.ok(Map.of(
                "scanned", scanned,
                "piiEntitiesFound", piiFound
        ));
    }

    /**
     * Trigger PII scan on a single document.
     */
    @PostMapping("/pii-scan/{documentId}")
    public ResponseEntity<Map<String, Object>> scanDocument(@PathVariable String documentId) {
        DocumentModel doc = documentService.getById(documentId);
        if (doc == null) return ResponseEntity.notFound().build();
        if (doc.getExtractedText() == null) return ResponseEntity.ok(Map.of("piiEntitiesFound", 0));

        List<PiiEntity> findings = piiScanner.scan(doc.getExtractedText());
        doc.setPiiFindings(findings);
        doc.setPiiScannedAt(Instant.now());
        documentService.save(doc);

        return ResponseEntity.ok(Map.of(
                "documentId", documentId,
                "piiEntitiesFound", findings.size(),
                "findings", findings
        ));
    }

    /**
     * Check for overlapping pipeline coverage across taxonomy categories.
     */
    @GetMapping("/overlap-check")
    public ResponseEntity<Map<String, List<PipelineOverlap>>> checkOverlaps() {
        return ResponseEntity.ok(routingService.checkOverlaps());
    }

    /**
     * Resolve which pipeline would handle a given category + mime type.
     */
    @GetMapping("/resolve")
    public ResponseEntity<Map<String, Object>> resolvePipeline(
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) String mimeType) {
        PipelineDefinition resolved = routingService.resolve(categoryId, mimeType);
        if (resolved == null) {
            return ResponseEntity.ok(Map.of("resolved", false));
        }
        return ResponseEntity.ok(Map.of(
                "resolved", true,
                "pipelineId", resolved.getId(),
                "pipelineName", resolved.getName(),
                "isDefault", resolved.isDefault()
        ));
    }

    /**
     * Test a pipeline node's connectivity and configuration.
     * For ACCELERATOR/GENERIC_HTTP nodes, makes a real HTTP call with sample data.
     * For BUILT_IN nodes, validates configuration.
     */
    @PostMapping("/test-node")
    public ResponseEntity<Map<String, Object>> testNode(@RequestBody Map<String, Object> body) {
        String nodeType = (String) body.get("nodeType");
        @SuppressWarnings("unchecked")
        Map<String, Object> config = (Map<String, Object>) body.getOrDefault("config", Map.of());

        if (nodeType == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "nodeType is required"));
        }

        var typeDef = nodeTypeService.getByKey(nodeType).orElse(null);
        if (typeDef == null) {
            return ResponseEntity.ok(Map.of("success", false, "error", "Unknown node type: " + nodeType));
        }

        String execCategory = typeDef.getExecutionCategory();
        var result = new LinkedHashMap<String, Object>();
        result.put("nodeType", nodeType);
        result.put("executionCategory", execCategory);

        try {
            switch (execCategory) {
                case "ACCELERATOR" -> {
                    // For accelerators with HTTP services (BERT), test connectivity
                    String serviceUrl = config.get("serviceUrl") != null
                            ? config.get("serviceUrl").toString() : null;
                    if (serviceUrl != null && !serviceUrl.isBlank()) {
                        result.putAll(testHttpService(serviceUrl, config));
                    } else {
                        // In-memory accelerator (rules, fingerprint, similarity) — just validate config
                        var handler = handlerRegistry.getAccelerator(nodeType);
                        result.put("success", true);
                        result.put("registered", handler.isPresent());
                        result.put("message", handler.isPresent()
                                ? "Accelerator handler registered and ready"
                                : "No handler registered for " + nodeType);
                    }
                }
                case "GENERIC_HTTP" -> {
                    String serviceUrl = config.get("serviceUrl") != null
                            ? config.get("serviceUrl").toString() : null;
                    if (serviceUrl == null || serviceUrl.isBlank()) {
                        // Fall back to httpConfig default
                        if (typeDef.getHttpConfig() != null) {
                            serviceUrl = typeDef.getHttpConfig().defaultUrl();
                        }
                    }
                    if (serviceUrl != null && !serviceUrl.isBlank()) {
                        result.putAll(testHttpService(serviceUrl, config));
                    } else {
                        result.put("success", false);
                        result.put("error", "No service URL configured");
                    }
                }
                case "SYNC_LLM" -> {
                    // Test LLM worker connectivity
                    String llmUrl = "http://llm-worker:8082";
                    result.putAll(testHttpService(llmUrl, Map.of()));
                    result.put("provider", config.getOrDefault("provider", "global default"));
                    result.put("model", config.getOrDefault("model", "global default"));
                }
                case "BUILT_IN" -> {
                    var handler = handlerRegistry.getHandler(nodeType);
                    result.put("success", true);
                    result.put("registered", handler.isPresent() || List.of(
                            "textExtraction", "piiScanner", "condition", "governance",
                            "humanReview", "notification").contains(nodeType));
                    result.put("message", "Built-in node — no external service to test");
                }
                case "NOOP" -> {
                    result.put("success", true);
                    result.put("message", "No-op node — nothing to test");
                }
                default -> {
                    result.put("success", false);
                    result.put("error", "Unknown execution category: " + execCategory);
                }
            }
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }

        return ResponseEntity.ok(result);
    }

    private Map<String, Object> testHttpService(String serviceUrl, Map<String, Object> config) {
        var result = new LinkedHashMap<String, Object>();
        result.put("serviceUrl", serviceUrl);

        try {
            var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

            // First: connectivity check (HEAD or GET to base URL)
            long start = System.currentTimeMillis();
            var healthReq = HttpRequest.newBuilder()
                    .uri(URI.create(serviceUrl))
                    .timeout(Duration.ofSeconds(5))
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .build();

            HttpResponse<String> healthResp;
            try {
                healthResp = client.send(healthReq, HttpResponse.BodyHandlers.ofString());
            } catch (Exception headEx) {
                // HEAD not supported — try GET
                var getReq = HttpRequest.newBuilder()
                        .uri(URI.create(serviceUrl))
                        .timeout(Duration.ofSeconds(5))
                        .GET()
                        .build();
                healthResp = client.send(getReq, HttpResponse.BodyHandlers.ofString());
            }
            long latencyMs = System.currentTimeMillis() - start;

            result.put("success", true);
            result.put("reachable", true);
            result.put("statusCode", healthResp.statusCode());
            result.put("latencyMs", latencyMs);
            result.put("message", "Service reachable (" + latencyMs + "ms)");

            // If it's a classify endpoint, try a sample request
            String path = config.get("path") != null ? config.get("path").toString() : "/classify";
            try {
                byte[] sampleBytes = "{\"text\":\"Test document for connectivity check\",\"document_id\":\"test-probe\",\"mime_type\":\"text/plain\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
                var reqBuilder = HttpRequest.newBuilder()
                        .uri(URI.create(serviceUrl + path))
                        .header("Content-Type", "application/json")
                        .header("Content-Length", String.valueOf(sampleBytes.length))
                        .timeout(Duration.ofSeconds(10))
                        .POST(HttpRequest.BodyPublishers.ofByteArray(sampleBytes));

                if (config.get("authToken") != null && !config.get("authToken").toString().isBlank()) {
                    reqBuilder.header("Authorization", "Bearer " + config.get("authToken"));
                }

                var classifyResp = client.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
                result.put("classifyStatus", classifyResp.statusCode());
                result.put("classifyResponse", classifyResp.body().length() > 500
                        ? classifyResp.body().substring(0, 500) + "..." : classifyResp.body());
            } catch (Exception classifyErr) {
                result.put("classifyError", classifyErr.getMessage());
            }

        } catch (java.net.ConnectException e) {
            result.put("success", false);
            result.put("reachable", false);
            result.put("error", "Connection refused: " + serviceUrl);
        } catch (java.net.http.HttpTimeoutException e) {
            result.put("success", false);
            result.put("reachable", false);
            result.put("error", "Timeout connecting to " + serviceUrl);
        } catch (Exception e) {
            result.put("success", false);
            result.put("reachable", false);
            result.put("error", e.getMessage());
        }

        return result;
    }
}
