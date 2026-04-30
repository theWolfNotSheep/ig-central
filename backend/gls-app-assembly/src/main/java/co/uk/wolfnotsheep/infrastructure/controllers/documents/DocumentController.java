package co.uk.wolfnotsheep.infrastructure.controllers.documents;

import java.util.List;
import java.util.Map;
import co.uk.wolfnotsheep.document.events.DocumentIngestedEvent;
import co.uk.wolfnotsheep.document.models.AuditEvent;
import co.uk.wolfnotsheep.document.models.DocumentModel;
import co.uk.wolfnotsheep.document.models.DocumentStatus;
import co.uk.wolfnotsheep.document.services.DocumentService;
import co.uk.wolfnotsheep.document.services.PiiPatternScanner;
import co.uk.wolfnotsheep.document.services.PiiRedactionService;
import co.uk.wolfnotsheep.document.models.PiiEntity;
import co.uk.wolfnotsheep.governance.models.BlockFeedback;
import co.uk.wolfnotsheep.governance.models.ClassificationCorrection;
import co.uk.wolfnotsheep.governance.models.ClassificationCorrection.CorrectionType;
import co.uk.wolfnotsheep.governance.models.DocumentClassificationResult;
import co.uk.wolfnotsheep.governance.models.MetadataSchema;
import co.uk.wolfnotsheep.governance.models.PipelineBlock;
import co.uk.wolfnotsheep.governance.models.MetadataSchema;
import co.uk.wolfnotsheep.governance.models.PiiTypeDefinition;
import co.uk.wolfnotsheep.governance.repositories.MetadataSchemaRepository;
import co.uk.wolfnotsheep.governance.repositories.BlockFeedbackRepository;
import co.uk.wolfnotsheep.governance.repositories.PipelineBlockRepository;
import co.uk.wolfnotsheep.governance.models.PiiTypeDefinition.ApprovalStatus;
import co.uk.wolfnotsheep.governance.repositories.PiiTypeDefinitionRepository;
import co.uk.wolfnotsheep.governance.services.GovernanceService;
import co.uk.wolfnotsheep.infrastructure.audit.PlatformAuditEmitter;
import co.uk.wolfnotsheep.infrastructure.services.DocumentAccessService;
import co.uk.wolfnotsheep.infrastructure.services.ElasticsearchIndexService;
import co.uk.wolfnotsheep.platformaudit.envelope.Outcome;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.sax.ToHTMLContentHandler;
import org.xml.sax.ContentHandler;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private static final String EXCHANGE = "gls.documents";
    private static final String ROUTING_INGESTED = "document.ingested";

    private final DocumentService documentService;
    private final GovernanceService governanceService;
    private final RabbitTemplate rabbitTemplate;
    private final PiiTypeDefinitionRepository piiTypeRepository;
    private final BlockFeedbackRepository blockFeedbackRepo;
    private final PipelineBlockRepository blockRepo;
    private final DocumentAccessService accessService;
    private final ElasticsearchIndexService esIndexService;
    private final PiiPatternScanner piiScanner;
    private final PiiRedactionService piiRedactionService;
    private final MetadataSchemaRepository metadataSchemaRepo;
    private final co.uk.wolfnotsheep.document.repositories.AuditEventRepository auditEventRepository;
    private final co.uk.wolfnotsheep.infrastructure.services.PipelineThrottleService throttleService;
    private final PlatformAuditEmitter platformAudit;

    public DocumentController(DocumentService documentService,
                              GovernanceService governanceService,
                              RabbitTemplate rabbitTemplate,
                              PiiTypeDefinitionRepository piiTypeRepository,
                              BlockFeedbackRepository blockFeedbackRepo,
                              PipelineBlockRepository blockRepo,
                              DocumentAccessService accessService,
                              ElasticsearchIndexService esIndexService,
                              PiiPatternScanner piiScanner,
                              PiiRedactionService piiRedactionService,
                              MetadataSchemaRepository metadataSchemaRepo,
                              co.uk.wolfnotsheep.document.repositories.AuditEventRepository auditEventRepository,
                              co.uk.wolfnotsheep.infrastructure.services.PipelineThrottleService throttleService,
                              PlatformAuditEmitter platformAudit) {
        this.documentService = documentService;
        this.governanceService = governanceService;
        this.rabbitTemplate = rabbitTemplate;
        this.piiTypeRepository = piiTypeRepository;
        this.blockFeedbackRepo = blockFeedbackRepo;
        this.blockRepo = blockRepo;
        this.accessService = accessService;
        this.esIndexService = esIndexService;
        this.piiScanner = piiScanner;
        this.piiRedactionService = piiRedactionService;
        this.metadataSchemaRepo = metadataSchemaRepo;
        this.auditEventRepository = auditEventRepository;
        this.throttleService = throttleService;
        this.platformAudit = platformAudit;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentModel> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String folderId,
            @RequestParam(required = false) String pipelineId,
            @AuthenticationPrincipal UserDetails user) {

        // Throttle check
        String throttleError = throttleService.checkThrottle(1);
        if (throttleError != null) {
            return ResponseEntity.status(429).body(null);
        }

        DocumentModel doc = documentService.ingest(file, user.getUsername(), null);
        if (folderId != null && !folderId.isBlank()) {
            doc.setFolderId(folderId);
            documentService.save(doc);
        }

        // Publish ingested event to kick off the processing pipeline
        try {
            rabbitTemplate.convertAndSend(EXCHANGE, ROUTING_INGESTED, new DocumentIngestedEvent(
                    doc.getId(),
                    doc.getOriginalFileName(),
                    doc.getMimeType(),
                    doc.getFileSizeBytes(),
                    doc.getStorageBucket(),
                    doc.getStorageKey(),
                    user.getUsername(),
                    Instant.now(),
                    pipelineId
            ));
        } catch (Exception e) {
            // Document saved but pipeline won't start — set to failed so it's not orphaned
            documentService.setError(doc.getId(), DocumentStatus.PROCESSING_FAILED,
                    "QUEUE", "Failed to publish to processing queue: " + e.getMessage());
        }

        // Index into Elasticsearch
        esIndexService.indexDocument(doc.getId());

        return ResponseEntity.ok(doc);
    }

    @GetMapping("/{id}")
    public ResponseEntity<DocumentModel> getById(
            @PathVariable String id,
            @AuthenticationPrincipal UserDetails user) {
        DocumentModel doc = documentService.getById(id);
        if (doc == null) return ResponseEntity.notFound().build();
        if (user instanceof co.uk.wolfnotsheep.platform.identity.models.UserModel um) {
            if (!accessService.canAccess(um, doc, "READ")) {
                auditEventRepository.save(new AuditEvent(id, "ACCESS_DENIED", um.getEmail(), "USER",
                        java.util.Map.of("action", "VIEW", "reason", "Insufficient permissions",
                                "sensitivity", doc.getSensitivityLabel() != null ? doc.getSensitivityLabel().name() : "",
                                "category", doc.getCategoryName() != null ? doc.getCategoryName() : "")));
                platformAudit.emitUserAction(id, "ACCESS_DENIED", "VIEW", um.getEmail(),
                        Outcome.FAILURE,
                        java.util.Map.of("sensitivity", doc.getSensitivityLabel() != null ? doc.getSensitivityLabel().name() : "",
                                "category", doc.getCategoryName() != null ? doc.getCategoryName() : ""),
                        java.util.Map.of("reason", "Insufficient permissions"));
                return ResponseEntity.status(403).build();
            }
        }
        auditEventRepository.save(new AuditEvent(id, "DOCUMENT_VIEWED", user.getUsername(), "USER",
                java.util.Map.of("fileName", doc.getOriginalFileName() != null ? doc.getOriginalFileName() : doc.getFileName())));
        platformAudit.emitUserAction(id, "DOCUMENT_VIEWED", "VIEW", user.getUsername(),
                Outcome.SUCCESS, java.util.Map.of(),
                java.util.Map.of("fileName", doc.getOriginalFileName() != null ? doc.getOriginalFileName() : doc.getFileName()));
        return ResponseEntity.ok(doc);
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<InputStreamResource> download(
            @PathVariable String id,
            @AuthenticationPrincipal UserDetails user) {
        DocumentModel doc = documentService.getById(id);
        if (doc == null) return ResponseEntity.notFound().build();
        if (user instanceof co.uk.wolfnotsheep.platform.identity.models.UserModel um) {
            if (!accessService.canAccess(um, doc, "READ")) {
                auditEventRepository.save(new AuditEvent(id, "ACCESS_DENIED", um.getEmail(), "USER",
                        java.util.Map.of("action", "DOWNLOAD", "reason", "Insufficient permissions",
                                "sensitivity", doc.getSensitivityLabel() != null ? doc.getSensitivityLabel().name() : "",
                                "category", doc.getCategoryName() != null ? doc.getCategoryName() : "")));
                platformAudit.emitUserAction(id, "ACCESS_DENIED", "DOWNLOAD", um.getEmail(),
                        Outcome.FAILURE,
                        java.util.Map.of("sensitivity", doc.getSensitivityLabel() != null ? doc.getSensitivityLabel().name() : "",
                                "category", doc.getCategoryName() != null ? doc.getCategoryName() : ""),
                        java.util.Map.of("reason", "Insufficient permissions"));
                return ResponseEntity.status(403).build();
            }
        }
        auditEventRepository.save(new AuditEvent(id, "DOCUMENT_DOWNLOADED", user.getUsername(), "USER",
                java.util.Map.of("fileName", doc.getOriginalFileName() != null ? doc.getOriginalFileName() : doc.getFileName())));
        platformAudit.emitUserAction(id, "DOCUMENT_DOWNLOADED", "DOWNLOAD", user.getUsername(),
                Outcome.SUCCESS, java.util.Map.of(),
                java.util.Map.of("fileName", doc.getOriginalFileName() != null ? doc.getOriginalFileName() : doc.getFileName()));

        InputStream stream = documentService.downloadFile(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + doc.getOriginalFileName() + "\"")
                .contentType(MediaType.parseMediaType(doc.getMimeType()))
                .contentLength(doc.getFileSizeBytes())
                .body(new InputStreamResource(stream));
    }

    @GetMapping("/{id}/classification")
    public ResponseEntity<List<DocumentClassificationResult>> getClassification(@PathVariable String id) {
        List<DocumentClassificationResult> results = governanceService.getClassificationHistory(id);
        return ResponseEntity.ok(results);
    }

    @GetMapping("/{id}/preview")
    public ResponseEntity<InputStreamResource> preview(@PathVariable String id) {
        DocumentModel doc = documentService.getById(id);
        if (doc == null) return ResponseEntity.notFound().build();

        InputStream stream = documentService.downloadFile(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(doc.getMimeType()))
                .contentLength(doc.getFileSizeBytes())
                .body(new InputStreamResource(stream));
    }

    /**
     * Render an Office document (docx, xlsx, pptx) as HTML for in-browser preview.
     */
    @GetMapping("/{id}/preview-html")
    public ResponseEntity<String> previewHtml(@PathVariable String id) {
        DocumentModel doc = documentService.getById(id);
        if (doc == null) return ResponseEntity.notFound().build();

        try {
            InputStream stream = documentService.downloadFile(id);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ContentHandler handler = new ToHTMLContentHandler(baos, "UTF-8");
            Metadata metadata = new Metadata();
            new org.apache.tika.parser.AutoDetectParser().parse(stream, handler, metadata);

            String bodyHtml = baos.toString(StandardCharsets.UTF_8);
            String html = """
                    <!DOCTYPE html>
                    <html><head><meta charset="UTF-8">
                    <style>
                      body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                             max-width: 800px; margin: 2rem auto; padding: 0 1.5rem;
                             font-size: 14px; line-height: 1.6; color: #1a1a1a; }
                      table { border-collapse: collapse; width: 100%%; margin: 1rem 0; }
                      th, td { border: 1px solid #d1d5db; padding: 0.5rem 0.75rem; text-align: left; }
                      th { background: #f3f4f6; font-weight: 600; }
                      h1 { font-size: 1.5rem; } h2 { font-size: 1.25rem; } h3 { font-size: 1.1rem; }
                      img { max-width: 100%%; height: auto; }
                    </style></head><body>%s</body></html>
                    """.formatted(bodyHtml);

            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_HTML)
                    .body(html);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Preview generation failed: " + e.getMessage());
        }
    }

    @GetMapping("/{id}/audit")
    public ResponseEntity<Page<AuditEvent>> getAuditTrail(
            @PathVariable String id, Pageable pageable) {
        return ResponseEntity.ok(documentService.getAuditTrail(id, pageable));
    }

    @GetMapping
    public ResponseEntity<Page<DocumentModel>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String sensitivity,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String classificationCode,
            @RequestParam(required = false) String mimeType,
            @AuthenticationPrincipal UserDetails user,
            Pageable pageable) {

        return ResponseEntity.ok(
                documentService.search(user.getUsername(), q, status, sensitivity, category, classificationCode, mimeType, pageable));
    }

    /**
     * Get documents for a specific classification category (for tree browsing).
     */
    @GetMapping("/by-category/{categoryId}")
    public ResponseEntity<List<DocumentModel>> byCategory(
            @PathVariable String categoryId,
            @RequestParam(required = false) String name,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(
                documentService.getByCategoryAndUploader(categoryId, name, user.getUsername()));
    }

    /**
     * Get unclassified documents (pending processing/classification).
     */
    @GetMapping("/unclassified")
    public ResponseEntity<List<DocumentModel>> unclassified(
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(
                documentService.getUnclassifiedByUploader(user.getUsername()));
    }

    @GetMapping("/by-slug/{slug}")
    public ResponseEntity<DocumentModel> getBySlug(
            @PathVariable String slug,
            @AuthenticationPrincipal UserDetails user) {
        DocumentModel doc = documentService.getBySlug(slug);
        if (doc == null) return ResponseEntity.notFound().build();
        if (user instanceof co.uk.wolfnotsheep.platform.identity.models.UserModel um) {
            if (!accessService.canAccess(um, doc, "READ")) {
                auditEventRepository.save(new AuditEvent(doc.getId(), "ACCESS_DENIED", um.getEmail(), "USER",
                        java.util.Map.of("action", "VIEW", "reason", "Insufficient permissions",
                                "sensitivity", doc.getSensitivityLabel() != null ? doc.getSensitivityLabel().name() : "",
                                "category", doc.getCategoryName() != null ? doc.getCategoryName() : "")));
                platformAudit.emitUserAction(doc.getId(), "ACCESS_DENIED", "VIEW", um.getEmail(),
                        Outcome.FAILURE,
                        java.util.Map.of("sensitivity", doc.getSensitivityLabel() != null ? doc.getSensitivityLabel().name() : "",
                                "category", doc.getCategoryName() != null ? doc.getCategoryName() : ""),
                        java.util.Map.of("reason", "Insufficient permissions"));
                return ResponseEntity.status(403).build();
            }
        }
        auditEventRepository.save(new AuditEvent(doc.getId(), "DOCUMENT_VIEWED", user.getUsername(), "USER",
                java.util.Map.of("fileName", doc.getOriginalFileName() != null ? doc.getOriginalFileName() : doc.getFileName())));
        platformAudit.emitUserAction(doc.getId(), "DOCUMENT_VIEWED", "VIEW", user.getUsername(),
                Outcome.SUCCESS, java.util.Map.of(),
                java.util.Map.of("fileName", doc.getOriginalFileName() != null ? doc.getOriginalFileName() : doc.getFileName()));
        return ResponseEntity.ok(doc);
    }

    /**
     * Run a specific pipeline on a document. Sets the pipelineId before re-queuing.
     */
    @PostMapping("/{id}/run-pipeline/{pipelineId}")
    public ResponseEntity<DocumentModel> runPipeline(
            @PathVariable String id,
            @PathVariable String pipelineId,
            @AuthenticationPrincipal UserDetails user) {
        DocumentModel doc = documentService.getById(id);
        if (doc == null) return ResponseEntity.notFound().build();

        doc.setPipelineId(pipelineId);
        doc.setPipelineSelectionMethod("MANUAL");
        documentService.save(doc);

        doc = documentService.clearErrorForReprocess(id);

        try {
            rabbitTemplate.convertAndSend(EXCHANGE, ROUTING_INGESTED, new DocumentIngestedEvent(
                    doc.getId(), doc.getOriginalFileName(), doc.getMimeType(), doc.getFileSizeBytes(),
                    doc.getStorageBucket(), doc.getStorageKey(), user.getUsername(), Instant.now(),
                    pipelineId
            ));
        } catch (Exception e) {
            documentService.setError(doc.getId(), DocumentStatus.PROCESSING_FAILED, "QUEUE", e.getMessage());
            return ResponseEntity.internalServerError().body(doc);
        }

        return ResponseEntity.ok(doc);
    }

    /**
     * Re-queue a document for processing. Clears any error state and increments retry count.
     */
    @PostMapping("/{id}/reprocess")
    public ResponseEntity<DocumentModel> reprocess(
            @PathVariable String id,
            @AuthenticationPrincipal UserDetails user) {
        DocumentModel doc = documentService.getById(id);
        if (doc == null) return ResponseEntity.notFound().build();

        doc = documentService.clearErrorForReprocess(id);

        try {
            rabbitTemplate.convertAndSend(EXCHANGE, ROUTING_INGESTED, new DocumentIngestedEvent(
                    doc.getId(),
                    doc.getOriginalFileName(),
                    doc.getMimeType(),
                    doc.getFileSizeBytes(),
                    doc.getStorageBucket(),
                    doc.getStorageKey(),
                    user.getUsername(),
                    Instant.now(),
                    null
            ));
        } catch (Exception e) {
            documentService.setError(doc.getId(), DocumentStatus.PROCESSING_FAILED, "QUEUE", e.getMessage());
            return ResponseEntity.internalServerError().body(doc);
        }

        return ResponseEntity.ok(doc);
    }

    /**
     * Cancel processing — resets document to UPLOADED without re-triggering the pipeline.
     */
    @PostMapping("/{id}/cancel")
    public ResponseEntity<DocumentModel> cancelProcessing(
            @PathVariable String id, @AuthenticationPrincipal UserDetails user) {
        DocumentModel doc = documentService.getById(id);
        if (doc == null) return ResponseEntity.notFound().build();

        doc.setStatus(DocumentStatus.UPLOADED);
        doc.setLastError(null);
        doc.setRetryCount(0);
        doc.setCancelledAt(Instant.now());
        documentService.save(doc);
        return ResponseEntity.ok(doc);
    }

    /**
     * Reclassify a document with an optional pipeline override.
     * Clears error state and re-queues for full pipeline processing.
     */
    @PostMapping("/{id}/reclassify")
    public ResponseEntity<DocumentModel> reclassify(
            @PathVariable String id,
            @RequestBody(required = false) ReclassifyRequest request,
            @AuthenticationPrincipal UserDetails user) {
        DocumentModel doc = documentService.getById(id);
        if (doc == null) return ResponseEntity.notFound().build();

        String pipelineOverride = request != null ? request.pipelineId() : null;
        if (pipelineOverride != null) {
            doc.setPipelineId(pipelineOverride);
            doc.setPipelineSelectionMethod("MANUAL");
        } else {
            doc.setPipelineId(null);
            doc.setPipelineSelectionMethod("AUTO");
        }
        documentService.save(doc);

        doc = documentService.clearErrorForReprocess(id);

        try {
            rabbitTemplate.convertAndSend(EXCHANGE, ROUTING_INGESTED, new DocumentIngestedEvent(
                    doc.getId(), doc.getOriginalFileName(), doc.getMimeType(), doc.getFileSizeBytes(),
                    doc.getStorageBucket(), doc.getStorageKey(), user.getUsername(), Instant.now(),
                    pipelineOverride
            ));
        } catch (Exception e) {
            documentService.setError(doc.getId(), DocumentStatus.PROCESSING_FAILED, "QUEUE", e.getMessage());
            return ResponseEntity.internalServerError().body(doc);
        }

        return ResponseEntity.ok(doc);
    }

    record ReclassifyRequest(String pipelineId) {}

    // ── Stage-Specific Re-run Endpoints ────────────────

    /**
     * Re-extract text only — re-runs Tika + PII scan without re-classifying.
     */
    @PostMapping("/{id}/rerun/extract")
    public ResponseEntity<DocumentModel> rerunExtract(
            @PathVariable String id, @AuthenticationPrincipal UserDetails user) {
        DocumentModel doc = documentService.getById(id);
        if (doc == null) return ResponseEntity.notFound().build();

        doc.setStatus(DocumentStatus.UPLOADED);
        doc.setCancelledAt(null);
        doc.setLastError(null);
        doc.setLastErrorStage(null);
        doc.setFailedAt(null);
        documentService.save(doc);

        try {
            rabbitTemplate.convertAndSend(EXCHANGE, ROUTING_INGESTED, new DocumentIngestedEvent(
                    doc.getId(), doc.getOriginalFileName(), doc.getMimeType(), doc.getFileSizeBytes(),
                    doc.getStorageBucket(), doc.getStorageKey(), user.getUsername(), Instant.now(),
                    null
            ));
        } catch (Exception e) {
            documentService.setError(doc.getId(), DocumentStatus.PROCESSING_FAILED, "QUEUE", e.getMessage());
        }
        return ResponseEntity.ok(doc);
    }

    /**
     * Re-scan PII only — runs regex scanner on existing extracted text.
     * Preserves previous dismissals.
     */
    @PostMapping("/{id}/rerun/pii")
    public ResponseEntity<DocumentModel> rerunPii(@PathVariable String id) {
        DocumentModel doc = documentService.getById(id);
        if (doc == null) return ResponseEntity.notFound().build();
        if (doc.getExtractedText() == null) return ResponseEntity.badRequest().build();

        var previousFindings = doc.getPiiFindings() != null
                ? doc.getPiiFindings() : java.util.List.<PiiEntity>of();
        var findings = piiScanner.scan(doc.getExtractedText(), previousFindings);
        doc.setPiiFindings(findings);
        doc.setPiiScannedAt(Instant.now());
        DocumentModel saved = documentService.save(doc);
        esIndexService.indexDocument(id);
        return ResponseEntity.ok(saved);
    }

    /**
     * Re-classify only — sends existing extracted text to LLM without re-extracting.
     */
    @PostMapping("/{id}/rerun/classify")
    public ResponseEntity<DocumentModel> rerunClassify(
            @PathVariable String id, @AuthenticationPrincipal UserDetails user) {
        DocumentModel doc = documentService.getById(id);
        if (doc == null) return ResponseEntity.notFound().build();
        if (doc.getExtractedText() == null) return ResponseEntity.badRequest().build();

        doc.setStatus(DocumentStatus.PROCESSED);
        doc.setCancelledAt(null);
        doc.setLastError(null);
        doc.setLastErrorStage(null);
        doc.setFailedAt(null);
        documentService.save(doc);

        // Publish directly to the classification queue (skipping extraction)
        try {
            rabbitTemplate.convertAndSend(EXCHANGE, "document.processed",
                    new co.uk.wolfnotsheep.document.events.DocumentProcessedEvent(
                            doc.getId(), doc.getOriginalFileName(), doc.getMimeType(),
                            doc.getFileSizeBytes(), doc.getExtractedText(),
                            doc.getStorageBucket() + "/" + doc.getStorageKey(),
                            user.getUsername(), Instant.now()
                    ));
        } catch (Exception e) {
            documentService.setError(doc.getId(), DocumentStatus.CLASSIFICATION_FAILED, "QUEUE", e.getMessage());
        }
        return ResponseEntity.ok(doc);
    }

    /**
     * Re-enforce governance only — re-applies retention, storage tier, policies.
     */
    @PostMapping("/{id}/rerun/enforce")
    public ResponseEntity<DocumentModel> rerunEnforce(
            @PathVariable String id, @AuthenticationPrincipal UserDetails user) {
        DocumentModel doc = documentService.getById(id);
        if (doc == null) return ResponseEntity.notFound().build();
        if (doc.getCategoryId() == null) return ResponseEntity.badRequest().build();

        doc.setCancelledAt(null);
        doc.setLastError(null);
        doc.setLastErrorStage(null);
        doc.setFailedAt(null);
        documentService.save(doc);

        // Publish to enforcement queue
        try {
            var classResult = governanceService.getClassificationHistory(id);
            if (classResult.isEmpty()) return ResponseEntity.badRequest().build();
            var latest = classResult.getFirst();

            rabbitTemplate.convertAndSend(EXCHANGE, "document.classified",
                    new co.uk.wolfnotsheep.document.events.DocumentClassifiedEvent(
                            doc.getId(), latest.getId(), latest.getCategoryId(),
                            latest.getCategoryName(), latest.getSensitivityLabel(),
                            latest.getTags(), latest.getApplicablePolicyIds(),
                            latest.getRetentionScheduleId(), latest.getConfidence(),
                            false, Instant.now()
                    ));
        } catch (Exception e) {
            documentService.setError(doc.getId(), DocumentStatus.ENFORCEMENT_FAILED, "QUEUE", e.getMessage());
        }
        return ResponseEntity.ok(doc);
    }

    /**
     * Dismiss a PII finding as a false positive.
     * Records the dismissal on the document and creates a PII_DISMISSED correction
     * that feeds back to the LLM to avoid the same false positive in future.
     */
    @PostMapping("/{id}/pii/{piiIndex}/dismiss")
    public ResponseEntity<DocumentModel> dismissPii(
            @PathVariable String id,
            @PathVariable int piiIndex,
            @RequestBody DismissPiiRequest request,
            @AuthenticationPrincipal UserDetails user) {

        DocumentModel doc = documentService.getById(id);
        if (doc == null) return ResponseEntity.notFound().build();
        if (doc.getPiiFindings() == null || piiIndex >= doc.getPiiFindings().size()) {
            return ResponseEntity.badRequest().build();
        }

        var pii = doc.getPiiFindings().get(piiIndex);
        pii.setDismissed(true);
        pii.setDismissedBy(user.getUsername());
        pii.setDismissalReason(request.reason());
        documentService.save(doc);

        // Record as a PII_DISMISSED correction for LLM feedback
        ClassificationCorrection correction = new ClassificationCorrection();
        correction.setDocumentId(id);
        correction.setOriginalCategoryId(doc.getCategoryId());
        correction.setOriginalCategoryName(doc.getCategoryName());
        correction.setOriginalSensitivity(doc.getSensitivityLabel());
        correction.setCorrectedCategoryId(doc.getCategoryId());
        correction.setCorrectedCategoryName(doc.getCategoryName());
        correction.setCorrectedSensitivity(doc.getSensitivityLabel());
        correction.setCorrectionType(CorrectionType.PII_DISMISSED);
        correction.setReason(request.reason());
        correction.setMimeType(doc.getMimeType());
        correction.setPiiCorrections(List.of(
                new ClassificationCorrection.PiiCorrection(
                        pii.getType(),
                        "FALSE POSITIVE: " + request.reason(),
                        request.context() != null ? request.context() : pii.getRedactedText()
                )
        ));
        correction.setCorrectedBy(user.getUsername());
        governanceService.saveCorrection(correction);

        // Wire feedback to the PII Regex block
        List<PipelineBlock> regexBlocks = blockRepo.findByTypeAndActiveTrueOrderByNameAsc(PipelineBlock.BlockType.REGEX_SET);
        if (!regexBlocks.isEmpty()) {
            PipelineBlock block = regexBlocks.getFirst();
            BlockFeedback feedback = new BlockFeedback();
            feedback.setBlockId(block.getId());
            feedback.setBlockVersion(block.getActiveVersion());
            feedback.setDocumentId(id);
            feedback.setUserEmail(user.getUsername());
            feedback.setType(BlockFeedback.FeedbackType.FALSE_POSITIVE);
            feedback.setDetails("PII dismissed: " + pii.getType() + " — " + request.reason());
            feedback.setOriginalValue(pii.getType() + ": " + pii.getRedactedText());
            feedback.setCorrectedValue("NOT PII: " + request.reason());
            blockFeedbackRepo.save(feedback);
            block.setFeedbackCount(blockFeedbackRepo.countByBlockId(block.getId()));
            blockRepo.save(block);
        }

        return ResponseEntity.ok(doc);
    }

    record DismissPiiRequest(String reason, String context) {}

    /**
     * Verify a PII finding — confirms it is genuine PII.
     * Creates an audit trail and marks the finding as human-verified.
     */
    @PostMapping("/{id}/pii/{piiIndex}/verify")
    public ResponseEntity<DocumentModel> verifyPii(
            @PathVariable String id,
            @PathVariable int piiIndex,
            @AuthenticationPrincipal UserDetails user) {

        DocumentModel doc = documentService.getById(id);
        if (doc == null) return ResponseEntity.notFound().build();
        if (doc.getPiiFindings() == null || piiIndex >= doc.getPiiFindings().size()) {
            return ResponseEntity.badRequest().build();
        }

        var pii = doc.getPiiFindings().get(piiIndex);
        pii.setVerified(true);
        pii.setVerifiedBy(user.getUsername());

        // Update PII status to REVIEWED if all non-dismissed findings are now verified
        long activeCount = doc.getPiiFindings().stream().filter(p -> !p.isDismissed()).count();
        long verifiedCount = doc.getPiiFindings().stream().filter(p -> !p.isDismissed() && p.isVerified()).count();
        if (activeCount > 0 && verifiedCount == activeCount) {
            doc.setPiiStatus("REVIEWED");
        }

        documentService.save(doc);
        return ResponseEntity.ok(doc);
    }

    /**
     * Preview redacted text — returns the extracted text with PII replaced by [REDACTED:TYPE].
     * Does not modify the document.
     */
    @GetMapping("/{id}/pii/redacted-text")
    public ResponseEntity<Map<String, Object>> getRedactedText(@PathVariable String id) {
        DocumentModel doc = documentService.getById(id);
        if (doc == null) return ResponseEntity.notFound().build();

        String redacted = piiRedactionService.redactText(doc);
        long activeCount = doc.getPiiFindings() != null
                ? doc.getPiiFindings().stream().filter(p -> !p.isDismissed()).count() : 0;

        return ResponseEntity.ok(Map.of(
                "documentId", id,
                "redactedText", redacted != null ? redacted : "",
                "activePiiCount", activeCount,
                "piiStatus", doc.getPiiStatus() != null ? doc.getPiiStatus() : "NONE"
        ));
    }

    /**
     * Apply redaction permanently — replaces extractedText with redacted version
     * and scrubs raw PII from findings. Irreversible.
     */
    @PostMapping("/{id}/pii/redact")
    public ResponseEntity<DocumentModel> applyRedaction(
            @PathVariable String id,
            @AuthenticationPrincipal UserDetails user) {
        DocumentModel doc = piiRedactionService.applyRedaction(id);
        if (doc == null) return ResponseEntity.notFound().build();
        esIndexService.indexDocument(id);
        return ResponseEntity.ok(doc);
    }

    /**
     * Remove a metadata field with scope control.
     * scope: "document" = this doc only, "type" = all of this mime type, "category" = all in this category
     */
    @PostMapping("/{id}/metadata/remove-field")
    public ResponseEntity<DocumentModel> removeMetadataField(
            @PathVariable String id,
            @RequestBody RemoveFieldRequest request,
            @AuthenticationPrincipal UserDetails user) {

        DocumentModel doc = documentService.getById(id);
        if (doc == null) return ResponseEntity.notFound().build();

        // Always remove from this document
        if (doc.getExtractedMetadata() != null) {
            var meta = new java.util.HashMap<>(doc.getExtractedMetadata());
            meta.remove(request.fieldName());
            doc.setExtractedMetadata(meta);
            documentService.save(doc);
        }

        // If scope is "category", remove from the schema
        if ("category".equals(request.scope()) && doc.getCategoryId() != null) {
            var schemas = governanceService.getSchemasForCategory(doc.getCategoryId());
            for (var schema : schemas) {
                if (schema.getFields() != null && schema.getFields().stream().anyMatch(f -> f.fieldName().equals(request.fieldName()))) {
                    schema.setFields(schema.getFields().stream()
                            .filter(f -> !f.fieldName().equals(request.fieldName())).toList());
                    schema.setUpdatedAt(Instant.now());
                    metadataSchemaRepo.save(schema);
                }
            }
        }

        // Create feedback for the AI — regardless of scope
        List<PipelineBlock> promptBlocks = blockRepo.findByTypeAndActiveTrueOrderByNameAsc(PipelineBlock.BlockType.PROMPT);
        if (!promptBlocks.isEmpty()) {
            PipelineBlock block = promptBlocks.getFirst();
            BlockFeedback feedback = new BlockFeedback();
            feedback.setBlockId(block.getId());
            feedback.setBlockVersion(block.getActiveVersion());
            feedback.setDocumentId(id);
            feedback.setUserEmail(user.getUsername());
            feedback.setType(BlockFeedback.FeedbackType.CORRECTION);
            feedback.setDetails("Metadata field removed: " + request.fieldName() +
                    " (scope: " + request.scope() + ")" +
                    (request.reason() != null ? " — " + request.reason() : ""));
            feedback.setOriginalValue(request.fieldName());
            feedback.setCorrectedValue("REMOVED");
            blockFeedbackRepo.save(feedback);
            block.setFeedbackCount(blockFeedbackRepo.countByBlockId(block.getId()));
            blockRepo.save(block);
        }

        return ResponseEntity.ok(doc);
    }

    record RemoveFieldRequest(String fieldName, String scope, String reason) {}

    /**
     * Get the metadata schema and current values for a document.
     * Returns the schema fields pre-filled with any existing extractedMetadata.
     */
    @GetMapping("/{id}/metadata-schema")
    public ResponseEntity<Map<String, Object>> getMetadataSchema(@PathVariable String id) {
        DocumentModel doc = documentService.getById(id);
        if (doc == null) return ResponseEntity.notFound().build();

        // Find schemas: by category first, then by mime type
        List<MetadataSchema> schemas = new java.util.ArrayList<>();
        if (doc.getCategoryId() != null) {
            schemas.addAll(governanceService.getSchemasForCategory(doc.getCategoryId()));
        }
        // Also check mime type schemas
        if (doc.getMimeType() != null) {
            var mimeSchemas = governanceService.getActiveMetadataSchemas().stream()
                    .filter(s -> s.getLinkedMimeTypes() != null && s.getLinkedMimeTypes().stream()
                            .anyMatch(mt -> doc.getMimeType().contains(mt)))
                    .toList();
            for (var ms : mimeSchemas) {
                if (schemas.stream().noneMatch(s -> s.getId().equals(ms.getId()))) {
                    schemas.add(ms);
                }
            }
        }

        Map<String, String> current = doc.getExtractedMetadata() != null ? doc.getExtractedMetadata() : Map.of();

        if (schemas.isEmpty()) {
            // Identify extra fields (not in any schema)
            return ResponseEntity.ok(Map.of(
                    "hasSchema", false,
                    "currentValues", current,
                    "extraFields", current.keySet().stream().filter(k -> !k.startsWith("_")).toList()
            ));
        }

        MetadataSchema primarySchema = schemas.getFirst();

        // Separate schema fields from extra fields
        var schemaFieldNames = primarySchema.getFields() != null
                ? primarySchema.getFields().stream().map(MetadataSchema.MetadataField::fieldName).toList()
                : List.<String>of();
        var extraFields = current.keySet().stream()
                .filter(k -> !k.startsWith("_") && !schemaFieldNames.contains(k))
                .toList();

        return ResponseEntity.ok(Map.of(
                "hasSchema", true,
                "schemaId", primarySchema.getId(),
                "schemaName", primarySchema.getName(),
                "fields", primarySchema.getFields() != null ? primarySchema.getFields() : List.of(),
                "currentValues", current,
                "extraFields", extraFields,
                "categoryName", doc.getCategoryName() != null ? doc.getCategoryName() : "",
                "mimeType", doc.getMimeType() != null ? doc.getMimeType() : ""
        ));
    }

    /**
     * Save extracted metadata for a document.
     */
    @PutMapping("/{id}/metadata")
    public ResponseEntity<DocumentModel> saveMetadata(
            @PathVariable String id,
            @RequestBody Map<String, String> metadata) {
        DocumentModel doc = documentService.getById(id);
        if (doc == null) return ResponseEntity.notFound().build();

        doc.setExtractedMetadata(metadata);
        DocumentModel saved = documentService.save(doc);

        // Also update the latest classification result if one exists
        List<DocumentClassificationResult> results = governanceService.getClassificationHistory(id);
        if (!results.isEmpty()) {
            DocumentClassificationResult latest = results.getFirst();
            latest.setExtractedMetadata(metadata);
            governanceService.saveClassificationResult(latest);
        }

        return ResponseEntity.ok(saved);
    }

    /**
     * Get approved, active PII types for the Flag PII UI.
     * Accessible to all authenticated users (not admin-only).
     */
    @GetMapping("/pii-types")
    public ResponseEntity<List<PiiTypeDefinition>> getActivePiiTypes() {
        return ResponseEntity.ok(
                piiTypeRepository.findByActiveTrueAndApprovalStatusOrderByCategoryAscDisplayNameAsc(
                        ApprovalStatus.APPROVED));
    }

    /**
     * Suggest a custom PII type. Goes into a PENDING queue for admin approval.
     */
    @PostMapping("/pii-types/suggest")
    public ResponseEntity<PiiTypeDefinition> suggestPiiType(
            @RequestBody PiiTypeDefinition suggestion,
            @AuthenticationPrincipal UserDetails user) {
        // Check if key already exists
        if (piiTypeRepository.findByKey(suggestion.getKey()).isPresent()) {
            return ResponseEntity.ok(piiTypeRepository.findByKey(suggestion.getKey()).get());
        }
        suggestion.setApprovalStatus(ApprovalStatus.PENDING);
        suggestion.setActive(false);
        suggestion.setSubmittedBy(user.getUsername());
        suggestion.setSubmittedAt(Instant.now());
        return ResponseEntity.ok(piiTypeRepository.save(suggestion));
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Long>> stats() {
        return ResponseEntity.ok(Map.of(
                "uploaded", documentService.countByStatus(DocumentStatus.UPLOADED),
                "processing", documentService.countByStatus(DocumentStatus.PROCESSING),
                "processingFailed", documentService.countByStatus(DocumentStatus.PROCESSING_FAILED),
                "classified", documentService.countByStatus(DocumentStatus.CLASSIFIED),
                "classificationFailed", documentService.countByStatus(DocumentStatus.CLASSIFICATION_FAILED),
                "enforcementFailed", documentService.countByStatus(DocumentStatus.ENFORCEMENT_FAILED),
                "reviewRequired", documentService.countByStatus(DocumentStatus.REVIEW_REQUIRED),
                "governanceApplied", documentService.countByStatus(DocumentStatus.GOVERNANCE_APPLIED),
                "inbox", documentService.countByStatus(DocumentStatus.INBOX),
                "filed", documentService.countByStatus(DocumentStatus.FILED)
        ));
    }
}
