package co.uk.wolfnotsheep.infrastructure.controllers.admin;

import co.uk.wolfnotsheep.document.events.DocumentIngestedEvent;
import co.uk.wolfnotsheep.document.models.*;
import co.uk.wolfnotsheep.document.repositories.NodeRunRepository;
import co.uk.wolfnotsheep.document.repositories.PipelineRunRepository;
import co.uk.wolfnotsheep.document.repositories.SystemErrorRepository;
import co.uk.wolfnotsheep.document.services.DocumentService;
import co.uk.wolfnotsheep.document.services.PiiPatternScanner;
import co.uk.wolfnotsheep.infrastructure.config.RabbitMqConfig;
import co.uk.wolfnotsheep.infrastructure.services.BulkReclassifyCostEstimator;
import co.uk.wolfnotsheep.infrastructure.services.ElasticsearchIndexService;
import co.uk.wolfnotsheep.infrastructure.services.MonitoringService;
import co.uk.wolfnotsheep.infrastructure.services.PipelineEventBroadcaster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/monitoring")
public class MonitoringController {

    private static final Logger log = LoggerFactory.getLogger(MonitoringController.class);

    private final MonitoringService monitoringService;
    private final RabbitTemplate rabbitTemplate;
    private final DocumentService documentService;
    private final MongoTemplate mongoTemplate;
    private final PipelineEventBroadcaster broadcaster;
    private final ElasticsearchIndexService esIndexService;
    private final SystemErrorRepository systemErrorRepo;
    private final PiiPatternScanner piiScanner;
    private final PipelineRunRepository pipelineRunRepo;
    private final NodeRunRepository nodeRunRepo;
    private final co.uk.wolfnotsheep.infrastructure.services.PipelineThrottleService throttleService;
    private final BulkReclassifyCostEstimator costEstimator;

    public MonitoringController(MonitoringService monitoringService,
                                RabbitTemplate rabbitTemplate,
                                DocumentService documentService,
                                MongoTemplate mongoTemplate,
                                PipelineEventBroadcaster broadcaster,
                                ElasticsearchIndexService esIndexService,
                                SystemErrorRepository systemErrorRepo,
                                PiiPatternScanner piiScanner,
                                PipelineRunRepository pipelineRunRepo,
                                NodeRunRepository nodeRunRepo,
                                co.uk.wolfnotsheep.infrastructure.services.PipelineThrottleService throttleService,
                                BulkReclassifyCostEstimator costEstimator) {
        this.throttleService = throttleService;
        this.monitoringService = monitoringService;
        this.rabbitTemplate = rabbitTemplate;
        this.documentService = documentService;
        this.mongoTemplate = mongoTemplate;
        this.broadcaster = broadcaster;
        this.esIndexService = esIndexService;
        this.systemErrorRepo = systemErrorRepo;
        this.piiScanner = piiScanner;
        this.pipelineRunRepo = pipelineRunRepo;
        this.nodeRunRepo = nodeRunRepo;
        this.costEstimator = costEstimator;
    }

    /**
     * SSE endpoint — streams real-time pipeline metrics and document status events.
     */
    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events() {
        return broadcaster.subscribe();
    }

    /**
     * Get all documents currently in the pipeline (not yet GOVERNANCE_APPLIED or failed).
     * Shows what's actively being processed with their current stage and timing.
     */
    @GetMapping("/pipeline/documents")
    public ResponseEntity<List<Map<String, Object>>> pipelineDocuments() {
        List<DocumentStatus> activeStatuses = List.of(
                DocumentStatus.UPLOADED, DocumentStatus.PROCESSING, DocumentStatus.PROCESSED,
                DocumentStatus.CLASSIFYING, DocumentStatus.CLASSIFIED, DocumentStatus.REVIEW_REQUIRED,
                DocumentStatus.PROCESSING_FAILED, DocumentStatus.CLASSIFICATION_FAILED, DocumentStatus.ENFORCEMENT_FAILED
        );
        Query query = Query.query(Criteria.where("status").in(activeStatuses))
                .with(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "updatedAt"))
                .limit(100);
        // Only project fields we need — not extractedText
        query.fields().include("originalFileName", "status", "categoryName", "sensitivityLabel",
                "lastError", "lastErrorStage", "failedAt", "retryCount", "createdAt", "updatedAt",
                "uploadedBy", "mimeType", "fileSizeBytes", "slug");

        List<DocumentModel> docs = mongoTemplate.find(query, DocumentModel.class);
        List<Map<String, Object>> result = docs.stream().map(d -> {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("id", d.getId());
            m.put("slug", d.getSlug());
            m.put("fileName", d.getOriginalFileName());
            m.put("status", d.getStatus() != null ? d.getStatus().name() : "UNKNOWN");
            m.put("categoryName", d.getCategoryName());
            m.put("sensitivityLabel", d.getSensitivityLabel() != null ? d.getSensitivityLabel().name() : null);
            m.put("uploadedBy", d.getUploadedBy());
            m.put("mimeType", d.getMimeType());
            m.put("fileSizeBytes", d.getFileSizeBytes());
            m.put("lastError", d.getLastError());
            m.put("lastErrorStage", d.getLastErrorStage());
            m.put("retryCount", d.getRetryCount());
            m.put("createdAt", d.getCreatedAt());
            m.put("updatedAt", d.getUpdatedAt());
            // Calculate how long it's been in current state
            if (d.getUpdatedAt() != null) {
                m.put("durationMs", Instant.now().toEpochMilli() - d.getUpdatedAt().toEpochMilli());
            }
            return m;
        }).toList();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(monitoringService.getServiceHealth());
    }

    @GetMapping("/pipeline")
    public ResponseEntity<Map<String, Object>> pipeline() {
        return ResponseEntity.ok(monitoringService.getPipelineMetrics());
    }

    @GetMapping("/pipeline/throttle")
    public ResponseEntity<Map<String, Object>> throttleStatus() {
        return ResponseEntity.ok(throttleService.getStatus());
    }

    @GetMapping("/infrastructure")
    public ResponseEntity<Map<String, Object>> infrastructure() {
        return ResponseEntity.ok(monitoringService.getInfrastructureStats());
    }

    /**
     * Purge a RabbitMQ queue to clear stuck/poison messages.
     */
    @PostMapping("/queues/{queueName}/purge")
    public ResponseEntity<Map<String, Object>> purgeQueue(@PathVariable String queueName) {
        try {
            rabbitTemplate.execute(channel -> {
                channel.queuePurge(queueName);
                return null;
            });
            return ResponseEntity.ok(Map.of("queue", queueName, "purged", true));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("queue", queueName, "purged", false, "error", e.getMessage()));
        }
    }

    /**
     * Restart a service by calling its actuator restart endpoint (if available)
     * or simply reporting health status to confirm it's reachable.
     * Actual container restart requires Docker access — this provides a health ping
     * and queue purge capability which resolves most stuck pipeline issues.
     */
    @PostMapping("/services/{serviceName}/ping")
    public ResponseEntity<Map<String, Object>> pingService(@PathVariable String serviceName) {
        return ResponseEntity.ok(monitoringService.pingService(serviceName));
    }

    /**
     * Reset stale documents (stuck in PROCESSING/CLASSIFYING > 10 min) back to UPLOADED
     * and re-queue them for processing.
     */
    @PostMapping("/pipeline/reset-stale")
    public ResponseEntity<Map<String, Object>> resetStaleDocuments() {
        List<DocumentModel> stale = mongoTemplate.find(
                Query.query(Criteria.where("status").in(
                        DocumentStatus.PROCESSING, DocumentStatus.CLASSIFYING,
                        DocumentStatus.UPLOADING)
                        .and("updatedAt").lt(Instant.now().minus(10, ChronoUnit.MINUTES))),
                DocumentModel.class);

        int reset = 0;
        for (DocumentModel doc : stale) {
            doc.setStatus(DocumentStatus.UPLOADED);
            documentService.save(doc);
            requeueDocument(doc);
            reset++;
        }

        log.info("Reset and re-queued {} stale documents", reset);
        return ResponseEntity.ok(Map.of("reset", reset, "requeued", reset));
    }

    /**
     * Cancel all in-progress pipeline work: purges all RabbitMQ queues first,
     * then resets in-flight documents to UPLOADED. Does NOT re-queue — use
     * "reset stale" or manually reprocess to restart processing.
     */
    @PostMapping("/pipeline/cancel-all")
    public ResponseEntity<Map<String, Object>> cancelPipeline() {
        // Purge all pipeline queues first to stop further processing
        String[] queues = {
                "gls.documents.ingested", "gls.documents.processed",
                "gls.documents.classified", "gls.documents.classification.failed"
        };
        int purgedQueues = 0;
        for (String queue : queues) {
            try {
                rabbitTemplate.execute(channel -> {
                    channel.queuePurge(queue);
                    return null;
                });
                purgedQueues++;
            } catch (Exception e) {
                log.warn("Failed to purge queue {}: {}", queue, e.getMessage());
            }
        }

        // Reset all in-flight documents
        List<DocumentStatus> inFlightStatuses = List.of(
                DocumentStatus.PROCESSING, DocumentStatus.CLASSIFYING, DocumentStatus.UPLOADING);

        List<DocumentModel> inFlight = mongoTemplate.find(
                Query.query(Criteria.where("status").in(inFlightStatuses)),
                DocumentModel.class);

        int reset = 0;
        for (DocumentModel doc : inFlight) {
            doc.setStatus(DocumentStatus.UPLOADED);
            documentService.save(doc);
            reset++;
        }

        log.info("Pipeline cancelled: {} documents reset, {} queues purged", reset, purgedQueues);
        return ResponseEntity.ok(Map.of(
                "documentsReset", reset,
                "queuesPurged", purgedQueues
        ));
    }

    /**
     * Retry all failed documents: clears error state, resets to UPLOADED, and re-queues.
     */
    @PostMapping("/pipeline/retry-failed")
    public ResponseEntity<Map<String, Object>> retryFailed() {
        List<DocumentModel> failed = mongoTemplate.find(
                Query.query(Criteria.where("status").in(
                        DocumentStatus.PROCESSING_FAILED,
                        DocumentStatus.CLASSIFICATION_FAILED,
                        DocumentStatus.ENFORCEMENT_FAILED)),
                DocumentModel.class);

        int retried = 0;
        for (DocumentModel doc : failed) {
            doc = documentService.clearErrorForReprocess(doc.getId());
            requeueDocument(doc);
            retried++;
        }

        log.info("Retried {} failed documents", retried);
        return ResponseEntity.ok(Map.of("retried", retried));
    }

    /**
     * Bulk reclassify documents matched by status filter and/or explicit id list.
     * Runs cost estimation up-front (mean of recent CLASSIFY usage) and supports
     * a {@code dryRun} mode that returns the estimate + matched count without
     * touching any documents — operators can confirm the spend before clicking
     * through.
     *
     * <p>Status filter accepts the {@code DocumentStatus} enum names. Common
     * picks: {@code CLASSIFIED}, {@code REVIEW_REQUIRED}, {@code GOVERNANCE_APPLIED}
     * (re-run governance + classification on already-handled docs after
     * model / prompt changes); failed states are better routed via
     * {@code /pipeline/retry-failed}.
     *
     * <p>Per-document path: {@code clearErrorForReprocess} → publish
     * {@code DocumentIngestedEvent} via the same helper as the per-document
     * reclassify endpoint, so the pipeline handles each doc the same way.
     */
    @PostMapping("/pipeline/bulk-reclassify")
    public ResponseEntity<Map<String, Object>> bulkReclassify(
            @RequestBody(required = false) BulkReclassifyRequest request) {
        BulkReclassifyRequest req = request == null ? new BulkReclassifyRequest() : request;
        boolean dryRun = req.dryRun != null && req.dryRun;
        int hardCap = req.hardCap != null && req.hardCap > 0 ? Math.min(req.hardCap, 5000) : 1000;

        List<DocumentModel> targets = resolveBulkReclassifyTargets(req, hardCap);
        BulkReclassifyCostEstimator.Estimate estimate =
                costEstimator.estimateForCount(targets.size());

        if (dryRun) {
            log.info("[bulk-reclassify dry-run] matched {} documents (estimate: ${})",
                    targets.size(), String.format("%.4f", estimate.estimatedTotalCostUsd()));
            return ResponseEntity.ok(Map.of(
                    "matched", targets.size(),
                    "queued", 0,
                    "skipped", 0,
                    "errors", List.of(),
                    "dryRun", true,
                    "estimate", estimate));
        }

        int queued = 0;
        int skipped = 0;
        List<String> errors = new java.util.ArrayList<>();
        for (DocumentModel doc : targets) {
            try {
                DocumentModel cleared = documentService.clearErrorForReprocess(doc.getId());
                if (requeueDocument(cleared)) {
                    queued++;
                } else {
                    skipped++;
                    errors.add("requeue failed: " + doc.getId());
                }
            } catch (RuntimeException e) {
                skipped++;
                errors.add(doc.getId() + ": " + truncate(e.getMessage(), 200));
                log.warn("bulk-reclassify error on {}: {}", doc.getId(), e.getMessage());
            }
        }

        log.info("[bulk-reclassify] queued {}, skipped {}, of {} matched", queued, skipped, targets.size());
        return ResponseEntity.ok(Map.of(
                "matched", targets.size(),
                "queued", queued,
                "skipped", skipped,
                "errors", errors,
                "dryRun", false,
                "estimate", estimate));
    }

    private List<DocumentModel> resolveBulkReclassifyTargets(BulkReclassifyRequest req, int hardCap) {
        // Explicit ids take precedence — operators picking specific documents.
        if (req.documentIds != null && !req.documentIds.isEmpty()) {
            List<String> ids = req.documentIds.size() > hardCap
                    ? req.documentIds.subList(0, hardCap) : req.documentIds;
            Query q = Query.query(Criteria.where("_id").in(ids));
            q.limit(hardCap);
            return mongoTemplate.find(q, DocumentModel.class);
        }
        // Status-based selection. Empty list defaults to CLASSIFIED.
        List<DocumentStatus> statuses = new java.util.ArrayList<>();
        if (req.statuses != null) {
            for (String s : req.statuses) {
                try { statuses.add(DocumentStatus.valueOf(s)); }
                catch (IllegalArgumentException ignored) { /* skip unknown */ }
            }
        }
        if (statuses.isEmpty()) {
            statuses = List.of(DocumentStatus.CLASSIFIED, DocumentStatus.GOVERNANCE_APPLIED);
        }
        Query q = Query.query(Criteria.where("status").in(statuses));
        q.limit(hardCap);
        return mongoTemplate.find(q, DocumentModel.class);
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    /** Body for {@code POST /pipeline/bulk-reclassify}. All fields optional. */
    public static class BulkReclassifyRequest {
        public List<String> statuses;
        public List<String> documentIds;
        public Boolean dryRun;
        public Integer hardCap;
    }

    /**
     * Re-queue a single document back into the ingestion pipeline.
     */
    /**
     * Reindex all documents into Elasticsearch.
     */
    @PostMapping("/search/reindex")
    public ResponseEntity<Map<String, Object>> reindexElasticsearch() {
        int indexed = esIndexService.reindexAll();
        return ResponseEntity.ok(Map.of("indexed", indexed));
    }

    // ── System Errors ──────────────────────────────────────

    @GetMapping("/errors")
    public ResponseEntity<Page<SystemError>> listErrors(Pageable pageable,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Boolean unresolved) {
        if (Boolean.TRUE.equals(unresolved)) {
            return ResponseEntity.ok(systemErrorRepo.findByResolvedFalseOrderByTimestampDesc(pageable));
        }
        if (category != null && !category.isBlank()) {
            return ResponseEntity.ok(systemErrorRepo.findByCategoryOrderByTimestampDesc(category, pageable));
        }
        return ResponseEntity.ok(systemErrorRepo.findByOrderByTimestampDesc(pageable));
    }

    @GetMapping("/errors/summary")
    public ResponseEntity<Map<String, Object>> errorSummary() {
        long total = systemErrorRepo.countByResolvedFalse();
        long critical = systemErrorRepo.countByResolvedFalseAndSeverity("CRITICAL");
        long last24h = systemErrorRepo.countByTimestampAfterAndResolvedFalse(Instant.now().minusSeconds(86400));
        return ResponseEntity.ok(Map.of(
                "unresolvedCount", total,
                "criticalCount", critical,
                "last24hCount", last24h
        ));
    }

    @PostMapping("/errors/{id}/resolve")
    public ResponseEntity<SystemError> resolveError(@PathVariable String id,
            @RequestBody Map<String, String> body, Authentication auth) {
        return systemErrorRepo.findById(id).map(error -> {
            error.setResolved(true);
            error.setResolvedBy(auth != null ? auth.getName() : "ADMIN");
            error.setResolvedAt(Instant.now());
            error.setResolution(body.getOrDefault("resolution", "Resolved"));
            return ResponseEntity.ok(systemErrorRepo.save(error));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/documents/{id}")
    public ResponseEntity<Map<String, Object>> deleteDocument(@PathVariable String id, Authentication auth) {
        DocumentModel doc = documentService.getById(id);
        if (doc == null) return ResponseEntity.notFound().build();
        String fileName = doc.getOriginalFileName();
        documentService.deleteDocument(id, auth != null ? auth.getName() : "ADMIN");
        try { esIndexService.removeDocument(id); } catch (Exception e) {
            log.warn("Failed to remove document {} from ES index: {}", id, e.getMessage());
        }
        return ResponseEntity.ok(Map.of("deleted", true, "documentId", id, "fileName", fileName != null ? fileName : "unknown"));
    }

    private boolean requeueDocument(DocumentModel doc) {
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMqConfig.EXCHANGE,
                    RabbitMqConfig.ROUTING_INGESTED,
                    new DocumentIngestedEvent(
                            doc.getId(),
                            doc.getOriginalFileName(),
                            doc.getMimeType(),
                            doc.getFileSizeBytes(),
                            doc.getStorageBucket(),
                            doc.getStorageKey(),
                            doc.getUploadedBy(),
                            Instant.now(),
                            null
                    )
            );
            return true;
        } catch (Exception e) {
            log.error("Failed to requeue document {}: {}", doc.getId(), e.getMessage());
            documentService.setError(doc.getId(), DocumentStatus.PROCESSING_FAILED, "QUEUE", "Requeue failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Batch PII re-scan — re-runs regex PII scanner on all documents that have extracted text.
     * Useful after adding new patterns to a REGEX_SET block.
     * Preserves existing dismissals. Returns count of documents scanned and new findings.
     */
    @PostMapping("/pii/batch-rescan")
    public ResponseEntity<Map<String, Object>> batchPiiRescan(
            @RequestParam(defaultValue = "500") int limit) {

        // Invalidate the pattern cache so new patterns are loaded
        piiScanner.invalidateCache();

        Query query = Query.query(Criteria.where("extractedText").ne(null));
        query.limit(limit);
        List<DocumentModel> docs = mongoTemplate.find(query, DocumentModel.class);

        int scanned = 0;
        int newFindings = 0;
        int updatedDocs = 0;

        for (DocumentModel doc : docs) {
            if (doc.getExtractedText() == null || doc.getExtractedText().isBlank()) continue;

            List<PiiEntity> previousFindings = doc.getPiiFindings() != null
                    ? doc.getPiiFindings() : List.of();
            long previousActive = previousFindings.stream().filter(p -> !p.isDismissed()).count();

            List<PiiEntity> findings = piiScanner.scan(doc.getExtractedText(), previousFindings);
            long currentActive = findings.stream().filter(p -> !p.isDismissed()).count();

            scanned++;

            if (currentActive != previousActive || findings.size() != previousFindings.size()) {
                doc.setPiiFindings(findings);
                doc.setPiiScannedAt(Instant.now());
                doc.setPiiStatus(currentActive > 0 ? "DETECTED" : "NONE");
                documentService.save(doc);
                updatedDocs++;
                newFindings += Math.max(0, currentActive - previousActive);
            }
        }

        log.info("Batch PII re-scan complete: {} scanned, {} updated, {} new findings",
                scanned, updatedDocs, newFindings);

        return ResponseEntity.ok(Map.of(
                "scanned", scanned,
                "updatedDocuments", updatedDocs,
                "newFindings", newFindings
        ));
    }

    // ── Pipeline Run Tracking ───────────────────────────────────────────

    /**
     * Get pipeline run summary counts by status.
     */
    @GetMapping("/pipeline/runs/summary")
    public ResponseEntity<Map<String, Object>> pipelineRunSummary() {
        return ResponseEntity.ok(Map.of(
                "running", pipelineRunRepo.countByStatus(PipelineRunStatus.RUNNING),
                "waiting", pipelineRunRepo.countByStatus(PipelineRunStatus.WAITING),
                "completed", pipelineRunRepo.countByStatus(PipelineRunStatus.COMPLETED),
                "failed", pipelineRunRepo.countByStatus(PipelineRunStatus.FAILED)
        ));
    }

    /**
     * Get recent pipeline runs with their status, timing, and error info.
     */
    @GetMapping("/pipeline/runs")
    public ResponseEntity<Page<PipelineRun>> pipelineRuns(
            @RequestParam(required = false) String status,
            Pageable pageable) {
        if (status != null) {
            try {
                PipelineRunStatus prs = PipelineRunStatus.valueOf(status.toUpperCase());
                return ResponseEntity.ok(pipelineRunRepo.findByStatus(prs, pageable));
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().build();
            }
        }
        return ResponseEntity.ok(pipelineRunRepo.findAll(pageable));
    }

    /**
     * Get a specific pipeline run with all its node runs.
     */
    @GetMapping("/pipeline/runs/{runId}")
    public ResponseEntity<Map<String, Object>> pipelineRunDetail(@PathVariable String runId) {
        return pipelineRunRepo.findById(runId)
                .map(run -> {
                    List<NodeRun> nodeRuns = nodeRunRepo.findByPipelineRunIdOrderByStartedAtAsc(runId);
                    return ResponseEntity.ok(Map.<String, Object>of(
                            "run", run,
                            "nodeRuns", nodeRuns
                    ));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get pipeline runs for a specific document.
     */
    @GetMapping("/pipeline/runs/by-document/{documentId}")
    public ResponseEntity<List<PipelineRun>> pipelineRunsByDocument(@PathVariable String documentId) {
        return ResponseEntity.ok(pipelineRunRepo.findByDocumentIdOrderByCreatedAtDesc(documentId));
    }

    /**
     * Get currently waiting pipeline runs (documents awaiting LLM response).
     */
    @GetMapping("/pipeline/runs/waiting")
    public ResponseEntity<List<PipelineRun>> waitingRuns() {
        Instant cutoff = Instant.now().minus(10, ChronoUnit.MINUTES);
        List<PipelineRun> stale = pipelineRunRepo.findByStatusAndUpdatedAtBefore(
                PipelineRunStatus.WAITING, cutoff);
        List<PipelineRun> allWaiting = pipelineRunRepo.findByStatus(
                PipelineRunStatus.WAITING, Pageable.unpaged()).getContent();
        // Mark stale runs
        for (PipelineRun run : allWaiting) {
            if (stale.contains(run)) {
                // Annotate in a way the frontend can detect
                run.setError("STALE: waiting > 10 minutes");
            }
        }
        return ResponseEntity.ok(allWaiting);
    }
}
