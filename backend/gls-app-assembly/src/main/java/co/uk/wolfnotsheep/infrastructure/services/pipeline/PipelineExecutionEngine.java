package co.uk.wolfnotsheep.infrastructure.services.pipeline;

import co.uk.wolfnotsheep.docprocessing.extraction.TextExtractionService;
import co.uk.wolfnotsheep.document.events.DocumentClassifiedEvent;
import co.uk.wolfnotsheep.document.events.DocumentIngestedEvent;
import co.uk.wolfnotsheep.document.events.DocumentProcessedEvent;
import co.uk.wolfnotsheep.document.events.LlmJobCompletedEvent;
import co.uk.wolfnotsheep.document.events.LlmJobRequestedEvent;
import co.uk.wolfnotsheep.document.models.*;
import co.uk.wolfnotsheep.document.repositories.NodeRunRepository;
import co.uk.wolfnotsheep.document.repositories.PipelineRunRepository;
import co.uk.wolfnotsheep.document.services.DocumentService;
import co.uk.wolfnotsheep.document.services.ObjectStorageService;
import co.uk.wolfnotsheep.document.services.PiiPatternScanner;
import co.uk.wolfnotsheep.document.services.PipelineStatusNotifier;
import co.uk.wolfnotsheep.enforcement.services.EnforcementService;
import co.uk.wolfnotsheep.governance.models.ClassificationCorrection;
import co.uk.wolfnotsheep.governance.models.DocumentClassificationResult;
import co.uk.wolfnotsheep.governance.models.PipelineBlock;
import co.uk.wolfnotsheep.governance.models.PipelineDefinition;
import co.uk.wolfnotsheep.governance.models.PipelineDefinition.VisualEdge;
import co.uk.wolfnotsheep.governance.models.PipelineDefinition.VisualNode;
import co.uk.wolfnotsheep.governance.repositories.DocumentClassificationResultRepository;
import co.uk.wolfnotsheep.governance.repositories.PipelineBlockRepository;
import co.uk.wolfnotsheep.governance.services.GovernanceService;
import co.uk.wolfnotsheep.governance.services.PipelineRoutingService;
import co.uk.wolfnotsheep.infrastructure.config.RabbitMqConfig;
import co.uk.wolfnotsheep.governance.models.NodeTypeDefinition;
import co.uk.wolfnotsheep.governance.services.NodeTypeDefinitionService;
import co.uk.wolfnotsheep.governance.models.SensitivityLabel;
import co.uk.wolfnotsheep.document.repositories.ConnectedDriveRepository;
import co.uk.wolfnotsheep.infrastructure.services.drives.GoogleDriveService;
import co.uk.wolfnotsheep.infrastructure.services.pipeline.accelerators.AcceleratorHandler;
import co.uk.wolfnotsheep.infrastructure.services.pipeline.accelerators.AcceleratorResult;
import co.uk.wolfnotsheep.infrastructure.services.pipeline.accelerators.SmartTruncationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.Instant;
import java.util.*;

/**
 * Pipeline execution engine that walks the visual graph defined in PipelineDefinition.
 *
 * Creates a PipelineRun to track overall execution and a NodeRun per node.
 * When an LLM node is encountered, the engine publishes an LlmJobRequestedEvent,
 * sets the PipelineRun to WAITING, and returns (releasing the thread).
 * When the LLM job completes, {@link #resumePipeline} picks up from the next node.
 */
@Service
@ConditionalOnProperty(name = "pipeline.execution-engine.enabled", havingValue = "true")
public class PipelineExecutionEngine {

    private static final Logger log = LoggerFactory.getLogger(PipelineExecutionEngine.class);

    /**
     * Result of Phase 1 execution. If an accelerator short-circuits the LLM,
     * {@code skippedLlm} is true and {@code classificationEvent} contains the
     * synthesised classification event for immediate Phase 2 execution.
     */
    public record Phase1Result(boolean skippedLlm, DocumentClassifiedEvent classificationEvent) {
        public static Phase1Result awaitingLlm() { return new Phase1Result(false, null); }
        public static Phase1Result shortCircuited(DocumentClassifiedEvent event) { return new Phase1Result(true, event); }
    }

    private final TextExtractionService textExtractionService;
    private final DocumentService documentService;
    private final ObjectStorageService objectStorage;
    private final PiiPatternScanner piiScanner;
    private final GovernanceService governanceService;
    private final EnforcementService enforcementService;
    private final PipelineRoutingService pipelineRoutingService;
    private final PipelineBlockRepository blockRepo;
    private final DocumentClassificationResultRepository classificationResultRepo;
    private final RabbitTemplate rabbitTemplate;
    private final PipelineStatusNotifier statusNotifier;
    private final co.uk.wolfnotsheep.document.repositories.SystemErrorRepository systemErrorRepo;

    // Data-driven dispatch
    private final NodeTypeDefinitionService nodeTypeService;
    private final PipelineNodeHandlerRegistry handlerRegistry;
    private final GenericHttpNodeExecutor genericHttpExecutor;
    private final SyncLlmNodeExecutor syncLlmExecutor;
    private final SmartTruncationService smartTruncationService;
    private final co.uk.wolfnotsheep.platform.config.services.AppConfigService appConfigService;
    private final co.uk.wolfnotsheep.infrastructure.services.BertTrainingDataCollector bertTrainingDataCollector;
    private final GoogleDriveService googleDriveService;
    private final ConnectedDriveRepository connectedDriveRepo;

    // Pipeline run tracking
    private final PipelineRunRepository pipelineRunRepo;
    private final NodeRunRepository nodeRunRepo;

    // Phase 1.3 cutover: optional sync HTTP path through gls-classifier-router.
    // Bean is conditionally registered via @ConditionalOnProperty; default OFF
    // — when absent, the engine keeps the existing async-Rabbit dispatch.
    private final ObjectProvider<ClassifierRouterClient> classifierRouterClientProvider;

    // Phase 1.8 PR3 — POLICY block resolver. Looked up after each
    // classification to log + audit the policy that applies; the
    // resolved scans / metadata schemas / governance policies are
    // dispatched in Phase 1.9 (Stage ④ scan dispatch).
    private final ObjectProvider<co.uk.wolfnotsheep.governance.services.PolicyBlockResolver> policyBlockResolverProvider;

    // Phase 1.9 PR2 — Stage ④ scan dispatcher. Iterates the resolved
    // POLICY block's requiredScans[] and runs each through the cascade
    // router. Always present (the underlying ScanRouterClient is the
    // optional bean); observe-only at this phase.
    private final PolicyScanDispatcher policyScanDispatcher;

    // Phase 1.9 PR3 — Stage ④ metadata extraction dispatcher. Iterates
    // metadataSchemaIds[] from the resolved POLICY block and runs each
    // through the cascade router (against the seeded
    // extract-metadata-${schemaId} PROMPT block). Observe-only.
    private final MetadataExtractionDispatcher metadataExtractionDispatcher;

    // Phase 1.10 PR3 — optional sync HTTP path through gls-enforcement-worker.
    // Bean is conditionally registered via @ConditionalOnProperty; default OFF
    // — when absent, the engine keeps calling the in-process EnforcementService.
    private final ObjectProvider<EnforcementWorkerClient> enforcementWorkerClientProvider;

    public PipelineExecutionEngine(TextExtractionService textExtractionService,
                                   DocumentService documentService,
                                   ObjectStorageService objectStorage,
                                   PiiPatternScanner piiScanner,
                                   GovernanceService governanceService,
                                   EnforcementService enforcementService,
                                   PipelineRoutingService pipelineRoutingService,
                                   PipelineBlockRepository blockRepo,
                                   DocumentClassificationResultRepository classificationResultRepo,
                                   RabbitTemplate rabbitTemplate,
                                   PipelineStatusNotifier statusNotifier,
                                   NodeTypeDefinitionService nodeTypeService,
                                   PipelineNodeHandlerRegistry handlerRegistry,
                                   GenericHttpNodeExecutor genericHttpExecutor,
                                   SyncLlmNodeExecutor syncLlmExecutor,
                                   SmartTruncationService smartTruncationService,
                                   co.uk.wolfnotsheep.document.repositories.SystemErrorRepository systemErrorRepo,
                                   co.uk.wolfnotsheep.platform.config.services.AppConfigService appConfigService,
                                   co.uk.wolfnotsheep.infrastructure.services.BertTrainingDataCollector bertTrainingDataCollector,
                                   GoogleDriveService googleDriveService,
                                   ConnectedDriveRepository connectedDriveRepo,
                                   PipelineRunRepository pipelineRunRepo,
                                   NodeRunRepository nodeRunRepo,
                                   ObjectProvider<ClassifierRouterClient> classifierRouterClientProvider,
                                   ObjectProvider<co.uk.wolfnotsheep.governance.services.PolicyBlockResolver> policyBlockResolverProvider,
                                   PolicyScanDispatcher policyScanDispatcher,
                                   MetadataExtractionDispatcher metadataExtractionDispatcher,
                                   ObjectProvider<EnforcementWorkerClient> enforcementWorkerClientProvider) {
        this.textExtractionService = textExtractionService;
        this.documentService = documentService;
        this.objectStorage = objectStorage;
        this.piiScanner = piiScanner;
        this.governanceService = governanceService;
        this.enforcementService = enforcementService;
        this.pipelineRoutingService = pipelineRoutingService;
        this.blockRepo = blockRepo;
        this.classificationResultRepo = classificationResultRepo;
        this.rabbitTemplate = rabbitTemplate;
        this.statusNotifier = statusNotifier;
        this.nodeTypeService = nodeTypeService;
        this.handlerRegistry = handlerRegistry;
        this.genericHttpExecutor = genericHttpExecutor;
        this.syncLlmExecutor = syncLlmExecutor;
        this.smartTruncationService = smartTruncationService;
        this.systemErrorRepo = systemErrorRepo;
        this.appConfigService = appConfigService;
        this.bertTrainingDataCollector = bertTrainingDataCollector;
        this.googleDriveService = googleDriveService;
        this.connectedDriveRepo = connectedDriveRepo;
        this.pipelineRunRepo = pipelineRunRepo;
        this.nodeRunRepo = nodeRunRepo;
        this.classifierRouterClientProvider = classifierRouterClientProvider;
        this.policyBlockResolverProvider = policyBlockResolverProvider;
        this.policyScanDispatcher = policyScanDispatcher;
        this.metadataExtractionDispatcher = metadataExtractionDispatcher;
        this.enforcementWorkerClientProvider = enforcementWorkerClientProvider;
    }

    // ── Main entry point: execute pipeline from the beginning ─────────

    /**
     * Execute the pipeline for a newly ingested document.
     * Creates a PipelineRun, walks the graph, and pauses at async LLM nodes.
     */
    public void executePipeline(DocumentIngestedEvent event) {
        String docId = event.documentId();
        log.info("[Engine] Pipeline starting for document: {} ({})", docId, event.fileName());

        try {
            DocumentModel doc = documentService.getById(docId);
            if (doc == null) { log.warn("[Engine] Document {} not found — skipping", docId); return; }
            if (doc.getCancelledAt() != null && event.ingestedAt() != null
                    && doc.getCancelledAt().isAfter(event.ingestedAt())) {
                log.info("[Engine] Document {} cancelled — skipping", docId); return;
            }
            if (doc.getStatus() != DocumentStatus.UPLOADED) {
                log.info("[Engine] Document {} status is {} (expected UPLOADED) — skipping", docId, doc.getStatus()); return;
            }

            // If the event carries a manual pipeline selection, set it on the doc
            if (event.pipelineId() != null) {
                doc.setPipelineId(event.pipelineId());
                doc.setPipelineSelectionMethod("MANUAL");
            } else if (doc.getPipelineSelectionMethod() == null) {
                doc.setPipelineSelectionMethod("AUTO");
            }

            PipelineDefinition pipeline = resolvePipeline(doc);
            if (pipeline != null) {
                doc.setPipelineId(pipeline.getId());
                documentService.save(doc);
            }

            List<VisualNode> executionOrder = topologicalSort(pipeline);
            List<String> executionPlan = executionOrder.stream().map(VisualNode::id).toList();

            // Debug: log node IDs and edges
            if (pipeline != null && pipeline.getVisualNodes() != null) {
                for (var vn : pipeline.getVisualNodes()) {
                    log.info("[Engine] VisualNode id={} type={} label={}", vn.id(), vn.type(), vn.label());
                }
            }
            if (pipeline != null && pipeline.getVisualEdges() != null) {
                for (var ve : pipeline.getVisualEdges()) {
                    log.info("[Engine] VisualEdge {} -> {} (handle={})", ve.source(), ve.target(), ve.sourceHandle());
                }
            }
            log.info("[Engine] Execution order: {}", executionPlan);

            // Create PipelineRun
            PipelineRun run = new PipelineRun();
            run.setDocumentId(docId);
            run.setOrganisationId(doc.getOrganisationId());
            run.setPipelineId(pipeline != null ? pipeline.getId() : null);
            run.setPipelineVersion(0); // PipelineDefinition versioning is future work
            run.setStatus(PipelineRunStatus.RUNNING);
            run.setCorrelationId(UUID.randomUUID().toString());
            run.setExecutionPlan(executionPlan);
            run.setCurrentNodeIndex(0);
            run.setStartedAt(Instant.now());
            run.setCreatedAt(Instant.now());
            run.setUpdatedAt(Instant.now());
            run = pipelineRunRepo.save(run);

            log.info("[Engine] Created PipelineRun {} for document {} ({} nodes)",
                    run.getId(), docId, executionOrder.size());

            // Walk the graph
            walkNodes(run, doc, event, pipeline, executionOrder, 0);

        } catch (Exception e) {
            log.error("[Engine] Pipeline failed for document {}: {}", docId, e.getMessage(), e);
            handleNodeError(docId, event.fileName(), "PIPELINE", e, null);
        }
    }

    // ── Resume: continue pipeline after async LLM completion ──────────

    /**
     * Resume a paused pipeline after an async LLM job completes.
     * Loads the PipelineRun, updates the NodeRun, and continues walking from the next node.
     */
    public void resumePipeline(LlmJobCompletedEvent completedEvent) {
        String pipelineRunId = completedEvent.pipelineRunId();
        log.info("[Engine] Resuming pipeline run {} after LLM job {}", pipelineRunId, completedEvent.jobId());

        PipelineRun run = pipelineRunRepo.findById(pipelineRunId).orElse(null);
        if (run == null) {
            log.error("[Engine] PipelineRun {} not found — cannot resume", pipelineRunId);
            return;
        }

        if (run.getStatus() != PipelineRunStatus.WAITING) {
            log.warn("[Engine] PipelineRun {} status is {} (expected WAITING) — ignoring late arrival",
                    pipelineRunId, run.getStatus());
            return;
        }

        // Update the NodeRun for the async node
        NodeRun nodeRun = nodeRunRepo.findById(completedEvent.nodeRunId()).orElse(null);
        if (nodeRun != null) {
            if (nodeRun.getStatus() != NodeRunStatus.WAITING) {
                log.warn("[Engine] NodeRun {} already {} — duplicate completion, ignoring",
                        nodeRun.getId(), nodeRun.getStatus());
                return;
            }

            nodeRun.setCompletedAt(Instant.now());
            nodeRun.setDurationMs(java.time.Duration.between(nodeRun.getStartedAt(), Instant.now()).toMillis());

            if (completedEvent.success()) {
                nodeRun.setStatus(NodeRunStatus.SUCCEEDED);
                Map<String, Object> output = new HashMap<>();
                if (completedEvent.classificationResultId() != null)
                    output.put("classificationResultId", completedEvent.classificationResultId());
                if (completedEvent.categoryId() != null)
                    output.put("categoryId", completedEvent.categoryId());
                if (completedEvent.categoryName() != null)
                    output.put("categoryName", completedEvent.categoryName());
                if (completedEvent.sensitivityLabel() != null)
                    output.put("sensitivityLabel", completedEvent.sensitivityLabel());
                output.put("confidence", completedEvent.confidence());
                output.put("requiresHumanReview", completedEvent.requiresHumanReview());
                if (completedEvent.tags() != null) output.put("tags", completedEvent.tags());
                if (completedEvent.retentionScheduleId() != null)
                    output.put("retentionScheduleId", completedEvent.retentionScheduleId());
                if (completedEvent.applicablePolicyIds() != null)
                    output.put("applicablePolicyIds", completedEvent.applicablePolicyIds());
                if (completedEvent.extractedMetadata() != null)
                    output.put("extractedMetadata", completedEvent.extractedMetadata());
                if (completedEvent.customResult() != null)
                    output.put("customResult", completedEvent.customResult());
                nodeRun.setOutput(output);
            } else {
                nodeRun.setStatus(NodeRunStatus.FAILED);
                nodeRun.setError(completedEvent.error());
            }
            nodeRunRepo.save(nodeRun);
        }

        // Store LLM result in shared context
        Map<String, Object> ctx = run.getSharedContext();
        if (completedEvent.success()) {
            ctx.put("classificationResultId", completedEvent.classificationResultId());
            ctx.put("categoryId", completedEvent.categoryId());
            ctx.put("categoryName", completedEvent.categoryName());
            ctx.put("sensitivityLabel", completedEvent.sensitivityLabel());
            ctx.put("confidence", completedEvent.confidence());
            ctx.put("requiresHumanReview", completedEvent.requiresHumanReview());
            if (completedEvent.tags() != null) ctx.put("tags", completedEvent.tags());
            if (completedEvent.retentionScheduleId() != null)
                ctx.put("retentionScheduleId", completedEvent.retentionScheduleId());
            if (completedEvent.applicablePolicyIds() != null)
                ctx.put("applicablePolicyIds", completedEvent.applicablePolicyIds());
            if (completedEvent.extractedMetadata() != null)
                ctx.put("extractedMetadata", completedEvent.extractedMetadata());
            if (completedEvent.customResult() != null)
                ctx.put("customResult", completedEvent.customResult());
        }

        // Update document with classification results
        DocumentModel doc = documentService.getById(run.getDocumentId());
        if (doc == null) {
            log.error("[Engine] Document {} not found during resume", run.getDocumentId());
            failRun(run, "RESUME", "Document not found during resume");
            return;
        }

        // Auto-collect training data if enabled
        if (completedEvent.success() && completedEvent.classificationResultId() != null) {
            try {
                var classResult = classificationResultRepo.findById(completedEvent.classificationResultId()).orElse(null);
                if (classResult != null) bertTrainingDataCollector.tryCollect(classResult);
            } catch (Exception e) {
                log.debug("Auto-collect failed for doc {}: {}", doc.getId(), e.getMessage());
            }
        }

        if (!completedEvent.success()) {
            log.error("[Engine] LLM job failed for doc {}: {}", doc.getId(), completedEvent.error());
            failRun(run, run.getCurrentNodeKey(), completedEvent.error());
            documentService.setError(doc.getId(), DocumentStatus.CLASSIFICATION_FAILED,
                    "LLM", completedEvent.error());
            return;
        }

        // Apply classification to document
        DocumentClassifiedEvent classifiedEvent = buildClassifiedEvent(completedEvent, doc.getId());
        applyClassificationToDocument(doc, classifiedEvent);

        // Resolve POLICY block for the just-classified category and stash
        // the effective policy in shared context. Phase 1.8 PR3 — observe-
        // only; Phase 1.9 (Stage ④ scan dispatch) consumes this to drive
        // the post-classify pipeline.
        resolveAndRecordPolicy(classifiedEvent, ctx);

        // Phase 1.9 PR2 — dispatch the resolved POLICY block's
        // requiredScans[] through the cascade router. Observe-only at
        // this phase: results land in `policyScanResults` (ctx) so PR3
        // and PR4 can read them; the engine doesn't gate on blocking
        // failures yet.
        dispatchPolicyScans(run, doc, ctx);

        // Phase 1.9 PR3 — dispatch the resolved POLICY block's
        // metadataSchemaIds[] through the cascade router. Each
        // schema id resolves to a seeded
        // `extract-metadata-${schemaId}` PROMPT block. Results land
        // under `policyExtractionResults` for PR4 to persist onto
        // the document + hand off to enforcement.
        dispatchMetadataExtraction(run, doc, ctx);

        // Phase 1.9 PR4 — persist the aggregated stage ④ results
        // onto the DocumentClassificationResult so downstream nodes
        // (governance / enforcement / indexing) read them as part of
        // the canonical record. Merges extracted metadata into
        // `extractedMetadata` (string-coerced) and stores scan
        // findings keyed by ref under `policyScanFindings`.
        persistPolicyResults(doc, ctx);

        // Store classification as the current context for post-classification nodes
        ctx.put("currentClassification", "applied");
        run.setSharedContext(ctx);

        // Resume walking from the next node
        run.setStatus(PipelineRunStatus.RUNNING);
        int nextIndex = run.getCurrentNodeIndex() + 1;
        run.setCurrentNodeIndex(nextIndex);
        run.setUpdatedAt(Instant.now());
        run = pipelineRunRepo.save(run);

        // Resolve pipeline and execution order
        PipelineDefinition pipeline = resolvePipeline(doc);
        List<VisualNode> executionOrder = topologicalSort(pipeline);

        // Reconstruct the ingested event for node handlers that need it
        DocumentIngestedEvent ingestedEvent = new DocumentIngestedEvent(
                doc.getId(), doc.getFileName(), doc.getMimeType(),
                doc.getFileSizeBytes(), doc.getStorageBucket(), doc.getStorageKey(),
                doc.getUploadedBy(), doc.getCreatedAt(), null);

        try {
            walkNodes(run, doc, ingestedEvent, pipeline, executionOrder, nextIndex);
        } catch (Exception e) {
            log.error("[Engine] Pipeline resume failed for doc {}: {}", doc.getId(), e.getMessage(), e);
            failRun(run, "RESUME", e.getMessage());
            handleNodeError(doc.getId(), doc.getFileName(), "PIPELINE_RESUME", e, null);
        }
    }

    // ── Core graph walker ─────────────────────────────────────────────

    /**
     * Walk pipeline nodes from the given startIndex.
     * Creates NodeRun records, dispatches each node, and pauses at async LLM nodes.
     */
    private void walkNodes(PipelineRun run, DocumentModel doc, DocumentIngestedEvent event,
                           PipelineDefinition pipeline, List<VisualNode> executionOrder, int startIndex) {
        String docId = doc.getId();

        // Build a classification context from shared context (for resumed pipelines)
        DocumentClassifiedEvent currentClassification = buildClassificationFromContext(run.getSharedContext(), docId);

        for (int i = startIndex; i < executionOrder.size(); i++) {
            VisualNode node = executionOrder.get(i);
            if (isNodeDisabled(node)) continue;

            String nodeType = node.type();
            doc.setPipelineNodeId(node.id());
            documentService.save(doc);

            // Update PipelineRun position
            run.setCurrentNodeKey(node.id());
            run.setCurrentNodeIndex(i);
            run.setUpdatedAt(Instant.now());
            pipelineRunRepo.save(run);

            NodeTypeDefinition typeDef = nodeTypeService.getByKey(nodeType).orElse(null);
            String execCategory = typeDef != null ? typeDef.getExecutionCategory() : null;
            if (execCategory == null) {
                log.debug("[Engine] Unknown node type: {} — skipping", nodeType);
                continue;
            }

            // Create NodeRun
            NodeRun nodeRun = new NodeRun();
            nodeRun.setPipelineRunId(run.getId());
            nodeRun.setDocumentId(docId);
            nodeRun.setNodeKey(node.id());
            nodeRun.setNodeType(nodeType);
            nodeRun.setExecutionCategory(execCategory);
            nodeRun.setStatus(NodeRunStatus.RUNNING);
            nodeRun.setStartedAt(Instant.now());
            nodeRun = nodeRunRepo.save(nodeRun);

            final DocumentModel currentDoc = doc;
            final DocumentClassifiedEvent currentClassRef = currentClassification;

            try {
                switch (execCategory) {
                    case "NOOP" -> {
                        completeNodeRun(nodeRun, null);
                    }

                    case "BUILT_IN" -> {
                        switch (nodeType) {
                            case "textExtraction" -> handleTextExtraction(doc, event, node);
                            case "piiScanner" -> handlePiiScan(doc, event, node);
                            case "smartTruncation" -> handleSmartTruncation(doc, node);
                            case "condition" -> {
                                if (currentClassification != null) {
                                    String nextNodeId = evaluateCondition(doc, currentClassification, node, pipeline);
                                    completeNodeRun(nodeRun, Map.of("branch", nextNodeId != null ? nextNodeId : "none"));
                                    if (nextNodeId != null) {
                                        executeBranch(run, doc, currentClassification, event, pipeline, executionOrder, nextNodeId);
                                        completePipelineRun(run);
                                        return;
                                    }
                                } else {
                                    log.warn("[Engine] Condition node '{}' before classification — skipping", node.label());
                                }
                            }
                            case "governance" -> {
                                if (currentClassification != null) handleGovernance(doc, currentClassification, node);
                                else log.warn("[Engine] Governance node before classification — skipping");
                            }
                            case "humanReview" -> {
                                if (currentClassification != null) handleHumanReview(doc, currentClassification, node);
                                else log.warn("[Engine] Human review node before classification — skipping");
                            }
                            case "notification" -> {
                                if (currentClassification != null) handleNotification(doc, currentClassification, node);
                            }
                            default -> handlerRegistry.getHandler(nodeType)
                                    .ifPresent(h -> h.handle(new NodeHandlerContext(currentDoc, node,
                                            buildMergedConfig(node), event, currentClassRef)));
                        }
                        if (!"condition".equals(nodeType)) {
                            completeNodeRun(nodeRun, null);
                        }
                    }

                    case "ACCELERATOR" -> {
                        Map<String, Object> mergedConfig = buildMergedConfig(node);
                        AcceleratorResult accel = handlerRegistry.getAccelerator(nodeType)
                                .map(a -> a.evaluate(currentDoc, mergedConfig))
                                .orElse(AcceleratorResult.miss());
                        statusNotifier.emitLog(doc.getId(),
                                doc.getOriginalFileName() != null ? doc.getOriginalFileName() : "",
                                "ACCELERATOR", "INFO",
                                nodeType + (accel.matched() ? " → HIT: " + accel.categoryName() : " → MISS"), null);
                        if (accel.matched()) {
                            currentClassification = applyAcceleratorResult(doc, accel);
                            storeClassificationInContext(run, currentClassification);
                            log.info("[Engine] Accelerator '{}' short-circuited LLM for doc {} → {}",
                                    nodeType, docId, accel.categoryName());
                        }
                        completeNodeRun(nodeRun, Map.of("matched", accel.matched()));
                    }

                    case "GENERIC_HTTP" -> {
                        Map<String, Object> httpConfig = buildMergedConfig(node);
                        AcceleratorResult httpResult = genericHttpExecutor.execute(currentDoc, httpConfig, nodeType);
                        statusNotifier.emitLog(doc.getId(),
                                doc.getOriginalFileName() != null ? doc.getOriginalFileName() : "",
                                "GENERIC_HTTP", "INFO",
                                nodeType + (httpResult.matched() ? " → HIT: " + httpResult.categoryName() : " → MISS"), null);
                        if (httpResult.matched()) {
                            currentClassification = applyAcceleratorResult(doc, httpResult);
                            storeClassificationInContext(run, currentClassification);
                        }
                        completeNodeRun(nodeRun, Map.of("matched", httpResult.matched()));
                    }

                    case "SYNC_LLM" -> {
                        // Check auto-classify toggle
                        if (!appConfigService.getValue("pipeline.processing.auto_classify", true)) {
                            log.info("[Engine] Auto-classify disabled — skipping LLM node for doc {}", docId);
                            nodeRun.setStatus(NodeRunStatus.SKIPPED);
                            nodeRun.setCompletedAt(Instant.now());
                            nodeRunRepo.save(nodeRun);
                            documentService.updateStatus(docId, DocumentStatus.PROCESSED, "SYSTEM");
                            completePipelineRun(run);
                            return;
                        }

                        // If there's already a classification from an accelerator, skip the LLM
                        if (currentClassification != null) {
                            log.info("[Engine] Skipping LLM node — already classified by accelerator");
                            nodeRun.setStatus(NodeRunStatus.SKIPPED);
                            nodeRun.setCompletedAt(Instant.now());
                            nodeRunRepo.save(nodeRun);
                            continue;
                        }

                        // Async LLM: publish job request and pause
                        Map<String, Object> mergedConfig = buildMergedConfig(node);
                        if (node.blockId() != null) mergedConfig.put("blockId", node.blockId());

                        String jobId = UUID.randomUUID().toString();
                        String idempotencyKey = run.getCorrelationId() + ":" + node.id();

                        nodeRun.setStatus(NodeRunStatus.WAITING);
                        nodeRun.setJobId(jobId);
                        nodeRun.setIdempotencyKey(idempotencyKey);
                        nodeRun = nodeRunRepo.save(nodeRun);

                        // Determine LLM mode
                        String blockId = mergedConfig.containsKey("blockId")
                                ? mergedConfig.get("blockId").toString() : null;
                        String mode = determineLlmMode(mergedConfig, blockId);

                        // Pause the pipeline run
                        run.setStatus(PipelineRunStatus.WAITING);
                        run.setCurrentNodeKey(node.id());
                        run.setCurrentNodeIndex(i);
                        run.setUpdatedAt(Instant.now());
                        pipelineRunRepo.save(run);

                        // Update document status
                        documentService.updateStatus(docId, DocumentStatus.CLASSIFYING, "SYSTEM");

                        // Phase 1.3 cutover: when the classifier-router client is wired
                        // (pipeline.classifier-router.enabled=true), call the router
                        // synchronously and apply the result inline via resumePipeline.
                        // The router internally dispatches via the existing LLM worker,
                        // so the underlying model call is unchanged — only the engine's
                        // transport changes.
                        ClassifierRouterClient routerClient = classifierRouterClientProvider.getIfAvailable();
                        if (routerClient != null) {
                            statusNotifier.emitLog(docId,
                                    doc.getOriginalFileName() != null ? doc.getOriginalFileName() : "",
                                    "LLM_CALL", "INFO",
                                    "Classify dispatched via gls-classifier-router for node '" + node.label() + "'",
                                    null);
                            LlmJobCompletedEvent completed;
                            try {
                                completed = routerClient.classify(jobId, run.getId(), nodeRun.getId(),
                                        blockId, /* blockVersion */ null,
                                        doc.getExtractedText(), idempotencyKey);
                            } catch (RuntimeException e) {
                                log.warn("[Engine] classifier-router call failed for jobId={}: {}",
                                        jobId, e.getMessage());
                                completed = LlmJobCompletedEvent.failure(jobId, run.getId(),
                                        nodeRun.getId(), "classifier-router: " + e.getMessage());
                            }
                            log.info("[Engine] classifier-router returned (jobId={} success={}) — resuming inline",
                                    jobId, completed.success());
                            resumePipeline(completed);
                            return; // Inline resume drove the rest of the pipeline.
                        }

                        // Default path: publish to Rabbit and release the thread.
                        LlmJobRequestedEvent jobEvent = new LlmJobRequestedEvent(
                                jobId,
                                run.getId(),
                                nodeRun.getId(),
                                docId,
                                node.id(),
                                mode,
                                blockId,
                                null, // blockVersion resolved by worker
                                doc.getPipelineId(),
                                doc.getExtractedText(),
                                doc.getFileName(),
                                doc.getMimeType(),
                                doc.getFileSizeBytes(),
                                doc.getUploadedBy(),
                                idempotencyKey
                        );

                        statusNotifier.emitLog(docId,
                                doc.getOriginalFileName() != null ? doc.getOriginalFileName() : "",
                                "LLM_CALL", "INFO",
                                "Async LLM job published for node '" + node.label() + "'", null);

                        rabbitTemplate.convertAndSend(
                                RabbitMqConfig.PIPELINE_EXCHANGE,
                                RabbitMqConfig.ROUTING_LLM_JOB_REQUESTED,
                                jobEvent);

                        log.info("[Engine] Published LlmJobRequestedEvent {} — pipeline WAITING at node {}",
                                jobId, node.id());
                        return; // Release thread — pipeline resumes when LLM completes
                    }

                    case "ASYNC_BOUNDARY" -> {
                        // Legacy fallback — publish to old RabbitMQ topology
                        log.info("[Engine] ASYNC_BOUNDARY fallback — publishing to RabbitMQ for doc {}", docId);
                        handleAiClassification(doc, event, node);
                        completeNodeRun(nodeRun, null);
                        // Mark run as waiting (legacy path)
                        run.setStatus(PipelineRunStatus.WAITING);
                        run.setUpdatedAt(Instant.now());
                        pipelineRunRepo.save(run);
                        return;
                    }

                    default -> {
                        log.debug("[Engine] Unknown execution category: {} for node {}", execCategory, nodeType);
                        completeNodeRun(nodeRun, null);
                    }
                }
            } catch (Exception e) {
                failNodeRun(nodeRun, e.getMessage());
                failRun(run, node.id(), e.getMessage());
                throw e; // re-throw so the outer catch handles document error status
            }

            // Data-driven document reload
            if (typeDef != null && typeDef.isRequiresDocReload()) {
                doc = documentService.getById(docId);
                if (doc == null) {
                    log.error("[Engine] Document {} disappeared after {} handler", docId, nodeType);
                    failRun(run, nodeType, "Document disappeared");
                    return;
                }
            }
        }

        // Pipeline complete — all nodes executed without pausing
        completePipelineRun(run);
        doc.setPipelineNodeId(null);
        documentService.save(doc);
        log.info("[Engine] Pipeline complete for document: {}", docId);
    }

    /**
     * Execute a condition branch: walks reachable nodes from startNodeId.
     */
    private void executeBranch(PipelineRun run, DocumentModel doc,
                               DocumentClassifiedEvent classification,
                               DocumentIngestedEvent ingestedEvent,
                               PipelineDefinition pipeline, List<VisualNode> executionOrder,
                               String startNodeId) {
        Set<String> reachable = new LinkedHashSet<>();
        Queue<String> bfs = new LinkedList<>();
        bfs.add(startNodeId);
        Map<String, List<String>> adj = buildAdjacency(pipeline);

        while (!bfs.isEmpty()) {
            String id = bfs.poll();
            if (reachable.add(id)) {
                for (String neighbor : adj.getOrDefault(id, List.of())) bfs.add(neighbor);
            }
        }

        for (VisualNode node : executionOrder) {
            if (!reachable.contains(node.id())) continue;
            if (isNodeDisabled(node)) continue;

            doc.setPipelineNodeId(node.id());
            documentService.save(doc);

            String nodeType = node.type();
            NodeTypeDefinition typeDef = nodeTypeService.getByKey(nodeType).orElse(null);
            String execCategory = typeDef != null ? typeDef.getExecutionCategory() : "BUILT_IN";

            NodeRun nodeRun = new NodeRun();
            nodeRun.setPipelineRunId(run.getId());
            nodeRun.setDocumentId(doc.getId());
            nodeRun.setNodeKey(node.id());
            nodeRun.setNodeType(nodeType);
            nodeRun.setExecutionCategory(execCategory);
            nodeRun.setStatus(NodeRunStatus.RUNNING);
            nodeRun.setStartedAt(Instant.now());
            nodeRun = nodeRunRepo.save(nodeRun);

            try {
                switch (execCategory) {
                    case "NOOP" -> { }
                    case "BUILT_IN" -> {
                        switch (nodeType) {
                            case "condition" -> {
                                String nextNodeId = evaluateCondition(doc, classification, node, pipeline);
                                if (nextNodeId != null) {
                                    completeNodeRun(nodeRun, Map.of("branch", nextNodeId));
                                    executeBranch(run, doc, classification, ingestedEvent, pipeline, executionOrder, nextNodeId);
                                    return;
                                }
                            }
                            case "governance" -> handleGovernance(doc, classification, node);
                            case "humanReview" -> handleHumanReview(doc, classification, node);
                            case "notification" -> handleNotification(doc, classification, node);
                            default -> {
                                final DocumentModel cd = doc;
                                handlerRegistry.getHandler(nodeType)
                                        .ifPresent(h -> h.handle(new NodeHandlerContext(cd, node,
                                                buildMergedConfig(node), ingestedEvent, classification)));
                            }
                        }
                    }
                    case "SYNC_LLM" -> {
                        // In branch context, use sync for custom prompts (classification already done)
                        Map<String, Object> mergedConfig = buildMergedConfig(node);
                        if (node.blockId() != null) mergedConfig.put("blockId", node.blockId());
                        SyncLlmNodeExecutor.SyncLlmResult llmResult = syncLlmExecutor.execute(doc, mergedConfig, nodeType, ingestedEvent);
                        if (llmResult.success() && llmResult.customResult() != null) {
                            mergeCustomResult(doc, llmResult.customResult(), node);
                        }
                    }
                    default -> log.debug("[Engine] Skipping {} node type in branch: {}", execCategory, nodeType);
                }
                completeNodeRun(nodeRun, null);
            } catch (Exception e) {
                failNodeRun(nodeRun, e.getMessage());
                throw e;
            }

            if (typeDef != null && typeDef.isRequiresDocReload()) {
                doc = documentService.getById(doc.getId());
                if (doc == null) return;
            }
        }

        doc.setPipelineNodeId(null);
        documentService.save(doc);
    }

    // ── Phase 1/Phase 2 (kept for backward compatibility) ─────────────

    public Phase1Result executePhase1(DocumentIngestedEvent event) {
        String docId = event.documentId();
        log.info("[Engine] Phase 1 starting for document: {} ({})", docId, event.fileName());

        try {
            DocumentModel doc = documentService.getById(docId);
            if (doc == null) {
                log.warn("[Engine] Skipping document {} — not found", docId);
                return Phase1Result.awaitingLlm();
            }
            if (doc.getCancelledAt() != null && event.ingestedAt() != null
                    && doc.getCancelledAt().isAfter(event.ingestedAt())) {
                log.info("[Engine] Skipping document {} — cancelled", docId);
                return Phase1Result.awaitingLlm();
            }
            if (doc.getStatus() != DocumentStatus.UPLOADED) {
                log.info("[Engine] Skipping document {} — status is {} (expected UPLOADED)", docId, doc.getStatus());
                return Phase1Result.awaitingLlm();
            }

            PipelineDefinition pipeline = resolvePipeline(doc);
            if (pipeline != null) {
                doc.setPipelineId(pipeline.getId());
                documentService.save(doc);
            }

            List<VisualNode> executionOrder = topologicalSort(pipeline);
            log.info("[Engine] Pipeline '{}' has {} nodes", pipeline != null ? pipeline.getName() : "default", executionOrder.size());

            for (VisualNode node : executionOrder) {
                if (isNodeDisabled(node)) continue;

                String nodeType = node.type();
                doc.setPipelineNodeId(node.id());
                documentService.save(doc);

                NodeTypeDefinition typeDef = nodeTypeService.getByKey(nodeType).orElse(null);
                String execCategory = typeDef != null ? typeDef.getExecutionCategory() : null;

                if (execCategory == null) {
                    log.debug("[Engine] Unknown pre-classification node type: {} — skipping", nodeType);
                } else {
                    final DocumentModel currentDoc = doc;
                    switch (execCategory) {
                        case "NOOP" -> { }
                        case "BUILT_IN" -> {
                            switch (nodeType) {
                                case "textExtraction" -> handleTextExtraction(doc, event, node);
                                case "piiScanner" -> handlePiiScan(doc, event, node);
                                case "smartTruncation" -> handleSmartTruncation(doc, node);
                                default -> handlerRegistry.getHandler(nodeType)
                                        .ifPresent(h -> h.handle(new NodeHandlerContext(currentDoc, node,
                                                buildMergedConfig(node), event, null)));
                            }
                        }
                        case "ACCELERATOR" -> {
                            Map<String, Object> mergedConfig = buildMergedConfig(node);
                            AcceleratorResult accel = handlerRegistry.getAccelerator(nodeType)
                                    .map(a -> a.evaluate(currentDoc, mergedConfig))
                                    .orElse(AcceleratorResult.miss());
                            statusNotifier.emitLog(doc.getId(),
                                    doc.getOriginalFileName() != null ? doc.getOriginalFileName() : "",
                                    "ACCELERATOR", "INFO",
                                    nodeType + (accel.matched() ? " → HIT: " + accel.categoryName() : " → MISS"), null);
                            if (accel.matched()) {
                                DocumentClassifiedEvent classifiedEvent = applyAcceleratorResult(doc, accel);
                                return Phase1Result.shortCircuited(classifiedEvent);
                            }
                        }
                        case "GENERIC_HTTP" -> {
                            Map<String, Object> httpConfig = buildMergedConfig(node);
                            AcceleratorResult httpResult = genericHttpExecutor.execute(currentDoc, httpConfig, nodeType);
                            statusNotifier.emitLog(doc.getId(),
                                    doc.getOriginalFileName() != null ? doc.getOriginalFileName() : "",
                                    "GENERIC_HTTP", "INFO",
                                    nodeType + (httpResult.matched() ? " → HIT: " + httpResult.categoryName() : " → MISS"), null);
                            if (httpResult.matched()) {
                                return Phase1Result.shortCircuited(applyAcceleratorResult(doc, httpResult));
                            }
                        }
                        case "ASYNC_BOUNDARY" -> {
                            handleAiClassification(doc, event, node);
                            return Phase1Result.awaitingLlm();
                        }
                        default -> log.debug("[Engine] Unknown execution category: {} for node {}", execCategory, nodeType);
                    }
                }

                if (typeDef != null && typeDef.isRequiresDocReload()) {
                    doc = documentService.getById(docId);
                    if (doc == null) {
                        log.error("[Engine] Document {} disappeared after {} handler", docId, nodeType);
                        return Phase1Result.awaitingLlm();
                    }
                }
            }

            log.info("[Engine] Phase 1 complete — no aiClassification node found in pipeline");
            return Phase1Result.awaitingLlm();

        } catch (Exception e) {
            log.error("[Engine] Phase 1 failed for document {}: {}", docId, e.getMessage(), e);
            handleNodeError(docId, event.fileName(), "PHASE_1", e, null);
            return Phase1Result.awaitingLlm();
        }
    }

    public void executePhase2(DocumentClassifiedEvent event) {
        String docId = event.documentId();
        log.info("[Engine] Phase 2 starting for document: {} (category: {})", docId, event.categoryName());

        try {
            DocumentModel doc = documentService.getById(docId);
            if (doc == null) { log.warn("[Engine] Skipping Phase 2 for {} — not found", docId); return; }
            if (doc.getStatus() != DocumentStatus.CLASSIFIED
                    && doc.getStatus() != DocumentStatus.REVIEW_REQUIRED) {
                log.info("[Engine] Skipping Phase 2 for {} — status is {}", docId, doc.getStatus()); return;
            }

            PipelineDefinition pipeline = null;
            if (doc.getPipelineId() != null) {
                pipeline = pipelineRoutingService.resolve(event.categoryId(), null);
            }
            if (pipeline == null) pipeline = resolvePipeline(doc);

            List<VisualNode> executionOrder = topologicalSort(pipeline);
            boolean pastClassification = false;
            for (VisualNode node : executionOrder) {
                if (!pastClassification) {
                    NodeTypeDefinition boundaryCheck = nodeTypeService.getByKey(node.type()).orElse(null);
                    if (boundaryCheck != null && "ASYNC_BOUNDARY".equals(boundaryCheck.getExecutionCategory())) {
                        pastClassification = true;
                    }
                    continue;
                }
                if (isNodeDisabled(node)) continue;

                String nodeType = node.type();
                doc.setPipelineNodeId(node.id());
                documentService.save(doc);

                executePhase2Node(doc, event, node, nodeType, pipeline, executionOrder);

                NodeTypeDefinition typeDef = nodeTypeService.getByKey(nodeType).orElse(null);
                if (typeDef != null && typeDef.isRequiresDocReload()) {
                    doc = documentService.getById(docId);
                    if (doc == null) { log.error("[Engine] Document {} disappeared", docId); return; }
                }
            }

            doc.setPipelineNodeId(null);
            documentService.save(doc);
            log.info("[Engine] Phase 2 complete for document: {}", docId);

        } catch (Exception e) {
            log.error("[Engine] Phase 2 failed for document {}: {}", docId, e.getMessage(), e);
            handleNodeError(docId, "", "PHASE_2", e, null);
        }
    }

    // ── PipelineRun/NodeRun helpers ─────────────────────────────────────

    private void completeNodeRun(NodeRun nodeRun, Map<String, Object> output) {
        nodeRun.setStatus(NodeRunStatus.SUCCEEDED);
        nodeRun.setCompletedAt(Instant.now());
        nodeRun.setDurationMs(java.time.Duration.between(nodeRun.getStartedAt(), Instant.now()).toMillis());
        if (output != null) nodeRun.setOutput(output);
        nodeRunRepo.save(nodeRun);
    }

    private void failNodeRun(NodeRun nodeRun, String error) {
        nodeRun.setStatus(NodeRunStatus.FAILED);
        nodeRun.setCompletedAt(Instant.now());
        nodeRun.setDurationMs(java.time.Duration.between(nodeRun.getStartedAt(), Instant.now()).toMillis());
        nodeRun.setError(error);
        nodeRunRepo.save(nodeRun);
    }

    private void completePipelineRun(PipelineRun run) {
        run.setStatus(PipelineRunStatus.COMPLETED);
        run.setCompletedAt(Instant.now());
        run.setTotalDurationMs(java.time.Duration.between(run.getStartedAt(), Instant.now()).toMillis());
        run.setUpdatedAt(Instant.now());
        pipelineRunRepo.save(run);
    }

    private void failRun(PipelineRun run, String nodeKey, String error) {
        run.setStatus(PipelineRunStatus.FAILED);
        run.setErrorNodeKey(nodeKey);
        run.setError(error);
        run.setCompletedAt(Instant.now());
        run.setTotalDurationMs(java.time.Duration.between(run.getStartedAt(), Instant.now()).toMillis());
        run.setUpdatedAt(Instant.now());
        pipelineRunRepo.save(run);
    }

    private void storeClassificationInContext(PipelineRun run, DocumentClassifiedEvent event) {
        Map<String, Object> ctx = run.getSharedContext();
        ctx.put("classificationResultId", event.classificationResultId());
        ctx.put("categoryId", event.categoryId());
        ctx.put("categoryName", event.categoryName());
        ctx.put("sensitivityLabel", event.sensitivityLabel() != null ? event.sensitivityLabel().name() : null);
        ctx.put("confidence", event.confidence());
        ctx.put("requiresHumanReview", event.requiresHumanReview());
        if (event.tags() != null) ctx.put("tags", event.tags());
        if (event.retentionScheduleId() != null) ctx.put("retentionScheduleId", event.retentionScheduleId());
        if (event.applicablePolicyIds() != null) ctx.put("applicablePolicyIds", event.applicablePolicyIds());
        run.setSharedContext(ctx);
        pipelineRunRepo.save(run);
    }

    /**
     * Reconstruct a DocumentClassifiedEvent from PipelineRun shared context.
     */
    @SuppressWarnings("unchecked")
    private DocumentClassifiedEvent buildClassificationFromContext(Map<String, Object> ctx, String docId) {
        if (ctx == null || !ctx.containsKey("categoryId")) return null;
        String sensLabel = ctx.get("sensitivityLabel") != null ? ctx.get("sensitivityLabel").toString() : null;
        SensitivityLabel sensitivity = null;
        if (sensLabel != null) {
            try { sensitivity = SensitivityLabel.valueOf(sensLabel); } catch (Exception ignored) {}
        }
        return new DocumentClassifiedEvent(
                docId,
                ctx.get("classificationResultId") != null ? ctx.get("classificationResultId").toString() : null,
                ctx.get("categoryId").toString(),
                ctx.get("categoryName") != null ? ctx.get("categoryName").toString() : null,
                sensitivity,
                ctx.get("tags") instanceof List<?> tags ? (List<String>) tags : List.of(),
                ctx.get("applicablePolicyIds") instanceof List<?> pols ? (List<String>) pols : List.of(),
                ctx.get("retentionScheduleId") != null ? ctx.get("retentionScheduleId").toString() : null,
                ctx.get("confidence") instanceof Number n ? n.doubleValue() : 0.0,
                ctx.get("requiresHumanReview") instanceof Boolean b ? b : false,
                Instant.now()
        );
    }

    private DocumentClassifiedEvent buildClassifiedEvent(LlmJobCompletedEvent e, String docId) {
        SensitivityLabel sensitivity = null;
        if (e.sensitivityLabel() != null) {
            try { sensitivity = SensitivityLabel.valueOf(e.sensitivityLabel()); } catch (Exception ignored) {}
        }
        return new DocumentClassifiedEvent(
                docId,
                e.classificationResultId(),
                e.categoryId(),
                e.categoryName(),
                sensitivity,
                e.tags() != null ? e.tags() : List.of(),
                e.applicablePolicyIds() != null ? e.applicablePolicyIds() : List.of(),
                e.retentionScheduleId(),
                e.confidence(),
                e.requiresHumanReview(),
                e.completedAt() != null ? e.completedAt() : Instant.now()
        );
    }

    /**
     * Resolve the POLICY block for the just-classified category and
     * stash key fields in the shared context so Phase 1.9's Stage ④
     * scan dispatch can read it without re-querying Mongo. Tolerant
     * of a missing resolver bean (test contexts) or no POLICY block
     * for the category — both leave the context untouched.
     */
    private void resolveAndRecordPolicy(DocumentClassifiedEvent event,
                                         java.util.Map<String, Object> ctx) {
        co.uk.wolfnotsheep.governance.services.PolicyBlockResolver resolver =
                policyBlockResolverProvider.getIfAvailable();
        if (resolver == null) return;
        if (event == null || event.categoryId() == null) return;
        try {
            String sensitivityName = event.sensitivityLabel() == null ? null : event.sensitivityLabel().name();
            resolver.resolveByCategoryId(event.categoryId()).ifPresent(policy -> {
                co.uk.wolfnotsheep.governance.models.PolicyBlock effective =
                        policy.effectiveFor(sensitivityName);
                ctx.put("policyCategoryId", effective.categoryId());
                ctx.put("policyRequiredScanCount", effective.requiredScans().size());
                ctx.put("policyRequiredScans", effective.requiredScans().stream()
                        .map(s -> {
                            java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
                            m.put("scanType", s.scanType());
                            m.put("ref", s.ref());
                            m.put("blocking", s.blocking());
                            return m;
                        }).toList());
                ctx.put("policyMetadataSchemaIds", effective.metadataSchemaIds());
                ctx.put("policyGovernancePolicyIds", effective.governancePolicyIds());
                log.info("[engine] resolved POLICY for categoryId={}, sensitivity={}: scans={} schemas={} policies={}",
                        event.categoryId(), sensitivityName,
                        effective.requiredScans().size(),
                        effective.metadataSchemaIds().size(),
                        effective.governancePolicyIds().size());
            });
        } catch (RuntimeException e) {
            // Fail-soft: a misbehaving resolver shouldn't take down the pipeline.
            log.warn("[engine] POLICY resolution failed for categoryId={}: {} — continuing without policy",
                    event.categoryId(), e.getMessage());
        }
    }

    /**
     * Phase 1.9 PR2. Read the {@code policyRequiredScans} list stashed
     * by {@link #resolveAndRecordPolicy} and dispatch each scan through
     * the cascade router. Records the per-scan outcomes under
     * {@code policyScanResults} in shared context. Tolerant of: no
     * scans (returns silently), no router client (records each scan as
     * not-dispatched), or dispatcher exceptions (logged + skipped).
     */
    @SuppressWarnings("unchecked")
    private void dispatchPolicyScans(PipelineRun run, DocumentModel doc,
                                     java.util.Map<String, Object> ctx) {
        Object scansObj = ctx.get("policyRequiredScans");
        if (!(scansObj instanceof List<?> rawList) || rawList.isEmpty()) {
            return;
        }
        List<co.uk.wolfnotsheep.governance.models.PolicyBlock.RequiredScan> scans = new ArrayList<>(rawList.size());
        for (Object item : rawList) {
            if (!(item instanceof Map<?, ?> m)) continue;
            Object scanType = m.get("scanType");
            Object ref = m.get("ref");
            Object blocking = m.get("blocking");
            if (ref == null) continue;
            scans.add(new co.uk.wolfnotsheep.governance.models.PolicyBlock.RequiredScan(
                    scanType == null ? null : scanType.toString(),
                    ref.toString(),
                    blocking instanceof Boolean b ? b : true));
        }
        if (scans.isEmpty()) return;

        try {
            List<PolicyScanResult> results = policyScanDispatcher.dispatch(
                    run.getId(), scans, doc.getExtractedText());
            ctx.put("policyScanResults", results.stream().map(this::scanResultToMap).toList());
        } catch (RuntimeException e) {
            log.warn("[engine] policy scan dispatch threw for doc {}: {} — observe-only, continuing",
                    doc.getId(), e.getMessage());
        }
    }

    private Map<String, Object> scanResultToMap(PolicyScanResult r) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("scanType", r.scanType());
        m.put("ref", r.ref());
        m.put("blocking", r.blocking());
        m.put("dispatched", r.dispatched());
        m.put("tierOfDecision", r.tierOfDecision());
        m.put("confidence", r.confidence());
        m.put("result", r.result());
        m.put("error", r.error());
        m.put("durationMs", r.durationMs());
        return m;
    }

    /**
     * Phase 1.9 PR3. Read the {@code policyMetadataSchemaIds} list
     * stashed by {@link #resolveAndRecordPolicy} and dispatch each
     * schema's extraction prompt through the cascade router. Records
     * the per-schema outcomes under {@code policyExtractionResults} in
     * shared context. Tolerant of: no schemas (returns silently), no
     * router client (records each schema as not-dispatched), or
     * dispatcher exceptions (logged + skipped).
     */
    @SuppressWarnings("unchecked")
    private void dispatchMetadataExtraction(PipelineRun run, DocumentModel doc,
                                            java.util.Map<String, Object> ctx) {
        Object schemasObj = ctx.get("policyMetadataSchemaIds");
        if (!(schemasObj instanceof List<?> rawList) || rawList.isEmpty()) {
            return;
        }
        List<String> schemaIds = new ArrayList<>(rawList.size());
        for (Object item : rawList) {
            if (item != null) schemaIds.add(item.toString());
        }
        if (schemaIds.isEmpty()) return;

        try {
            List<MetadataExtractionResult> results = metadataExtractionDispatcher.dispatch(
                    run.getId(), schemaIds, doc.getExtractedText());
            ctx.put("policyExtractionResults",
                    results.stream().map(this::extractionResultToMap).toList());
        } catch (RuntimeException e) {
            log.warn("[engine] metadata extraction dispatch threw for doc {}: {} — observe-only, continuing",
                    doc.getId(), e.getMessage());
        }
    }

    private Map<String, Object> extractionResultToMap(MetadataExtractionResult r) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("schemaId", r.schemaId());
        m.put("blockRef", r.blockRef());
        m.put("dispatched", r.dispatched());
        m.put("tierOfDecision", r.tierOfDecision());
        m.put("confidence", r.confidence());
        m.put("extractedFields", r.extractedFields());
        m.put("error", r.error());
        m.put("durationMs", r.durationMs());
        return m;
    }

    /**
     * Phase 1.9 PR4. Persist the stage ④ scan + extraction results
     * onto the {@link DocumentClassificationResult} so downstream
     * nodes read them as part of the canonical record. Reads
     * {@code policyScanResults} + {@code policyExtractionResults}
     * from shared context and:
     *
     * <ul>
     *   <li>Merges every successful extraction's {@code extractedFields}
     *       into the existing {@code extractedMetadata} map (string-
     *       coerced for compatibility with the existing field type).
     *       Existing keys are not overwritten — classification-time
     *       metadata wins on conflict.
     *   <li>Stores the raw scan results, keyed by scan ref, under
     *       {@code policyScanFindings} on the classification result.
     * </ul>
     *
     * <p>Tolerant of: no classification result on the document
     * (returns silently), no scan or extraction results in ctx
     * (skips the corresponding update), classification result lookup
     * failures (logged, doesn't fail the pipeline).
     */
    @SuppressWarnings("unchecked")
    private void persistPolicyResults(DocumentModel doc, java.util.Map<String, Object> ctx) {
        String classResultId = doc.getClassificationResultId();
        if (classResultId == null) return;

        List<Map<String, Object>> scanResults =
                (List<Map<String, Object>>) ctx.getOrDefault("policyScanResults", List.of());
        List<Map<String, Object>> extractionResults =
                (List<Map<String, Object>>) ctx.getOrDefault("policyExtractionResults", List.of());
        if (scanResults.isEmpty() && extractionResults.isEmpty()) return;

        DocumentClassificationResult result;
        try {
            result = classificationResultRepo.findById(classResultId).orElse(null);
        } catch (Exception e) {
            log.warn("[engine] policy results persist: classification result {} lookup failed: {}",
                    classResultId, e.getMessage());
            return;
        }
        if (result == null) {
            log.warn("[engine] policy results persist: classification result {} not found", classResultId);
            return;
        }

        boolean changed = false;
        if (!extractionResults.isEmpty()) {
            Map<String, String> merged = mergeExtractedFields(
                    result.getExtractedMetadata(), extractionResults);
            int added = merged.size() - (result.getExtractedMetadata() == null
                    ? 0 : result.getExtractedMetadata().size());
            if (added > 0) {
                result.setExtractedMetadata(merged);
                log.info("[engine] policy results persist: merged {} extracted field(s) onto {}",
                        added, classResultId);
                changed = true;
            }
        }

        if (!scanResults.isEmpty()) {
            Map<String, Object> findings = aggregateScanFindings(scanResults);
            if (!findings.isEmpty()) {
                result.setPolicyScanFindings(findings);
                log.info("[engine] policy results persist: stored {} scan finding(s) on {}",
                        findings.size(), classResultId);
                changed = true;
            }
        }

        if (!changed) return;
        try {
            classificationResultRepo.save(result);
        } catch (RuntimeException e) {
            log.warn("[engine] policy results persist: save failed for {}: {} — observe-only, continuing",
                    classResultId, e.getMessage());
        }
    }

    /**
     * Merge {@code extractedFields} from successful extraction results
     * into the existing {@code extractedMetadata} map. Existing keys
     * are not overwritten — classification-time metadata wins on
     * conflict. Extracted in package-private form for unit testing.
     */
    static Map<String, String> mergeExtractedFields(
            Map<String, String> existing,
            List<Map<String, Object>> extractionResults) {
        Map<String, String> merged = existing == null
                ? new java.util.LinkedHashMap<>()
                : new java.util.LinkedHashMap<>(existing);
        if (extractionResults == null) return merged;
        for (Map<String, Object> r : extractionResults) {
            if (r == null) continue;
            if (Boolean.FALSE.equals(r.get("dispatched"))) continue;
            if (r.get("error") != null) continue;
            Object fieldsObj = r.get("extractedFields");
            if (!(fieldsObj instanceof Map<?, ?> fields)) continue;
            for (Map.Entry<?, ?> entry : fields.entrySet()) {
                String key = entry.getKey() == null ? null : entry.getKey().toString();
                if (key == null || key.isBlank()) continue;
                if (merged.containsKey(key)) continue;
                Object value = entry.getValue();
                if (value == null) continue;
                merged.put(key, value.toString());
            }
        }
        return merged;
    }

    /**
     * Build a {@code policyScanFindings} map keyed by scan {@code ref}
     * from the per-scan result rows. Skips rows with null ref. Last
     * row for a duplicate ref wins (the dispatcher dedupes upstream so
     * duplicates are unexpected). Extracted for unit testing.
     */
    static Map<String, Object> aggregateScanFindings(List<Map<String, Object>> scanResults) {
        Map<String, Object> findings = new java.util.LinkedHashMap<>();
        if (scanResults == null) return findings;
        for (Map<String, Object> r : scanResults) {
            if (r == null) continue;
            Object ref = r.get("ref");
            if (ref == null) continue;
            findings.put(ref.toString(), r);
        }
        return findings;
    }

    private void applyClassificationToDocument(DocumentModel doc, DocumentClassifiedEvent event) {
        doc.setCategoryId(event.categoryId());
        doc.setCategoryName(event.categoryName());
        doc.setSensitivityLabel(event.sensitivityLabel());
        doc.setClassificationResultId(event.classificationResultId());
        doc.setTags(event.tags());
        doc.setClassifiedAt(event.classifiedAt());
        doc.setStatus(DocumentStatus.CLASSIFIED);
        documentService.save(doc);
    }

    /**
     * Determine LLM mode from merged config and block content.
     */
    private String determineLlmMode(Map<String, Object> config, String blockId) {
        // Check explicit mode override
        Object modeOverride = config.get("llmMode");
        if (modeOverride != null) return modeOverride.toString();

        // Check block content for classification markers
        if (blockId != null) {
            var block = blockRepo.findById(blockId).orElse(null);
            if (block != null) {
                var content = block.getActiveContent();
                if (content != null) {
                    String systemPrompt = content.get("systemPrompt") != null ? content.get("systemPrompt").toString() : "";
                    if (systemPrompt.contains("save_classification_result")) return "CLASSIFICATION";
                }
            }
        }
        return "CLASSIFICATION"; // default
    }

    // ── Node handlers ───────────────────────────────────────────────────

    private void handleTrigger(DocumentModel doc, VisualNode node) {
        log.debug("[Engine] Trigger node: {} — entry point", node.label());
    }

    private void handleTextExtraction(DocumentModel doc, DocumentIngestedEvent event, VisualNode node) {
        documentService.updateStatus(doc.getId(), DocumentStatus.PROCESSING, "SYSTEM");
        long start = System.currentTimeMillis();
        statusNotifier.emitLog(doc.getId(), event.fileName(), "EXTRACTION", "INFO",
                "Starting text extraction", null);

        try {
            int maxTextLength = 500_000;
            boolean extractDublinCore = true;
            boolean extractMetadata = true;

            String blockId = node.blockId();
            Map<String, Object> blockContent = loadBlockContent(blockId, PipelineBlock.BlockType.EXTRACTOR);
            if (blockContent != null) {
                maxTextLength = toInt(blockContent.get("maxTextLength"), maxTextLength);
                extractDublinCore = toBool(blockContent.get("extractDublinCore"), true);
                extractMetadata = toBool(blockContent.get("extractMetadata"), true);
            }

            // Resolve content stream: MinIO (cache mode) or Google Drive API (stream mode)
            InputStream fileStream;
            if (event.storageKey() != null && !event.storageKey().isBlank()) {
                fileStream = objectStorage.download(event.storageBucket(), event.storageKey());
            } else if ("GOOGLE_DRIVE".equals(doc.getStorageProvider()) && doc.getExternalStorageRef() != null) {
                String fileId = doc.getExternalStorageRef().get("fileId");
                String driveId = doc.getConnectedDriveId();
                var drive = connectedDriveRepo.findById(driveId)
                        .orElseThrow(() -> new RuntimeException("Connected drive not found: " + driveId));
                fileStream = googleDriveService.downloadContent(drive, fileId, doc.getMimeType());
                log.info("[Engine] Stream mode — downloading directly from Google Drive for doc {}", doc.getId());
            } else {
                throw new RuntimeException("No storage source available for document " + doc.getId());
            }

            TextExtractionService.ExtractionResult result =
                    textExtractionService.extract(fileStream, event.fileName(),
                            maxTextLength, extractDublinCore, extractMetadata);

            long extractMs = System.currentTimeMillis() - start;
            statusNotifier.emitLog(doc.getId(), event.fileName(), "EXTRACTION", "INFO",
                    "Extracted " + result.text().length() + " chars, " + result.pageCount() + " pages", extractMs);

            doc.setExtractedText(result.text());
            doc.setPageCount(result.pageCount());
            doc.setDublinCore(result.dublinCore());
            documentService.save(doc);
        } catch (Exception e) {
            log.error("[Engine] Text extraction failed for {}: {}", doc.getId(), e.getMessage(), e);
            statusNotifier.emitLog(doc.getId(), event.fileName(), "EXTRACTION", "ERROR",
                    "Extraction failed: " + e.getMessage(), System.currentTimeMillis() - start);
            throw new PipelineStageException("EXTRACTION", e);
        }
    }

    private void handlePiiScan(DocumentModel doc, DocumentIngestedEvent event, VisualNode node) {
        long start = System.currentTimeMillis();

        try {
            List<PiiEntity> previousFindings = new ArrayList<>();
            if (doc.getPiiFindings() != null) {
                previousFindings.addAll(doc.getPiiFindings());
            }
            for (ClassificationCorrection c : governanceService.getCorrectionsByDocumentId(doc.getId())) {
                if (c.getCorrectionType() == ClassificationCorrection.CorrectionType.PII_DISMISSED
                        && c.getPiiCorrections() != null) {
                    for (var pc : c.getPiiCorrections()) {
                        PiiEntity synthetic = new PiiEntity();
                        synthetic.setType(pc.type());
                        synthetic.setMatchedText(pc.context());
                        synthetic.setDismissed(true);
                        synthetic.setDismissedBy(c.getCorrectedBy());
                        synthetic.setDismissalReason(c.getReason());
                        previousFindings.add(synthetic);
                    }
                }
            }
            for (ClassificationCorrection c : governanceService.getPiiDismissals()) {
                if (c.getPiiCorrections() != null) {
                    for (var pc : c.getPiiCorrections()) {
                        PiiEntity synthetic = new PiiEntity();
                        synthetic.setType(pc.type());
                        synthetic.setMatchedText(pc.context());
                        synthetic.setDismissed(true);
                        synthetic.setDismissedBy(c.getCorrectedBy());
                        synthetic.setDismissalReason(c.getReason());
                        previousFindings.add(synthetic);
                    }
                }
            }

            List<PiiEntity> piiFindings = piiScanner.scan(doc.getExtractedText(), previousFindings);
            doc.setPiiFindings(piiFindings);
            doc.setPiiScannedAt(Instant.now());
            long activePii = piiFindings.stream().filter(p -> !p.isDismissed()).count();
            doc.setPiiStatus(activePii > 0 ? "DETECTED" : "NONE");

            long piiMs = System.currentTimeMillis() - start;
            statusNotifier.emitLog(doc.getId(), event.fileName(), "PII_SCAN", "INFO",
                    activePii > 0 ? activePii + " PII entities detected" : "No PII detected", piiMs);

            documentService.save(doc);
            documentService.updateStatus(doc.getId(), DocumentStatus.PROCESSED, "SYSTEM");
            statusNotifier.emitLog(doc.getId(), event.fileName(), "EXTRACTION", "INFO",
                    "Document processed — queued for classification", null);
        } catch (Exception e) {
            log.error("[Engine] PII scan failed for {}: {}", doc.getId(), e.getMessage(), e);
            statusNotifier.emitLog(doc.getId(), event.fileName(), "PII_SCAN", "ERROR",
                    "PII scan failed: " + e.getMessage(), System.currentTimeMillis() - start);
            throw new PipelineStageException("PII_SCAN", e);
        }
    }

    private void handleAiClassification(DocumentModel doc, DocumentIngestedEvent event, VisualNode node) {
        var processedEvent = new DocumentProcessedEvent(
                doc.getId(), event.fileName(), event.mimeType(), event.fileSizeBytes(),
                doc.getExtractedText(),
                event.storageBucket() + "/" + event.storageKey(),
                event.uploadedBy(), Instant.now()
        );

        try {
            rabbitTemplate.convertAndSend(
                    RabbitMqConfig.EXCHANGE,
                    RabbitMqConfig.ROUTING_PROCESSED,
                    processedEvent);
            log.info("[Engine] Published to LLM queue for document: {} — pausing at node {}",
                    doc.getId(), node.id());
        } catch (Exception e) {
            log.error("[Engine] Failed to publish to LLM queue for {}: {}", doc.getId(), e.getMessage());
            documentService.setError(doc.getId(), DocumentStatus.PROCESSING_FAILED,
                    "QUEUE", "Failed to publish to classification queue: " + e.getMessage());
        }
    }

    private String evaluateCondition(DocumentModel doc, DocumentClassifiedEvent event,
                                     VisualNode node, PipelineDefinition pipeline) {
        Map<String, Object> data = node.data() != null ? new HashMap<>(node.data()) : new HashMap<>();
        Map<String, Object> blockContent = loadBlockContent(node.blockId(), PipelineBlock.BlockType.ROUTER);
        if (blockContent != null) data.putAll(blockContent);

        String field = data.getOrDefault("field", "confidence").toString();
        String operator = data.getOrDefault("operator", ">=").toString();
        double threshold = toDouble(data.get("threshold"), 0.7);

        double value = switch (field) {
            case "confidence" -> event.confidence();
            default -> 0.0;
        };

        boolean result = switch (operator) {
            case ">=" -> value >= threshold;
            case ">" -> value > threshold;
            case "<=" -> value <= threshold;
            case "<" -> value < threshold;
            case "==" -> value == threshold;
            default -> value >= threshold;
        };

        log.info("[Engine] Condition '{}': {} {} {} = {} → {}",
                node.label(), field, operator, threshold, value, result ? "TRUE" : "FALSE");

        String handleId = result ? "true" : "false";
        if (pipeline != null && pipeline.getVisualEdges() != null) {
            for (VisualEdge edge : pipeline.getVisualEdges()) {
                if (edge.source().equals(node.id()) && handleId.equals(edge.sourceHandle())) {
                    return edge.target();
                }
            }
        }

        log.warn("[Engine] No edge found for condition '{}' branch '{}'", node.label(), handleId);
        return null;
    }

    private void handleGovernance(DocumentModel doc, DocumentClassifiedEvent event, VisualNode node) {
        // Check auto-enforce toggle
        if (!appConfigService.getValue("pipeline.governance.auto_enforce", true)) {
            log.info("[Engine] Auto-enforce disabled — skipping governance for doc {}", doc.getId());
            statusNotifier.emitLog(doc.getId(), "", "ENFORCEMENT", "INFO",
                    "Governance skipped — auto-enforce disabled", null);
            return;
        }

        // Respect per-node config toggles
        Map<String, Object> config = buildMergedConfig(node);
        boolean applyRetention = !"false".equals(String.valueOf(config.getOrDefault("retention", "true")));
        boolean applyStorage = !"false".equals(String.valueOf(config.getOrDefault("storage", "true")));
        boolean applyPolicies = !"false".equals(String.valueOf(config.getOrDefault("policies", "true")));

        if (!applyRetention && !applyStorage && !applyPolicies) {
            log.info("[Engine] All governance toggles disabled on node '{}' — skipping for doc {}", node.label(), doc.getId());
            statusNotifier.emitLog(doc.getId(), "", "ENFORCEMENT", "INFO",
                    "Governance skipped — all toggles off on node", null);
            return;
        }

        long start = System.currentTimeMillis();
        statusNotifier.emitLog(doc.getId(), "", "ENFORCEMENT", "INFO",
                "Applying governance: " + event.categoryName() + " / " + event.sensitivityLabel()
                + " [retention=" + applyRetention + ", storage=" + applyStorage + ", policies=" + applyPolicies + "]", null);

        try {
            DocumentModel enforced = applyEnforcement(doc, event, node);
            if (enforced != null) {
                // Clear fields the admin toggled off
                if (!applyRetention) {
                    enforced.setRetentionScheduleId(null);
                    enforced.setRetentionExpiresAt(null);
                }
                if (!applyStorage) {
                    enforced.setStorageTierId(null);
                }
                // Only locally-uploaded documents go to triage for filing.
                // External storage documents (Google Drive, Gmail, etc.) are classified
                // in-situ and skip the filing triage queue.
                if ("LOCAL".equals(enforced.getStorageProvider())) {
                    enforced.setStatus(DocumentStatus.TRIAGE);
                } else {
                    enforced.setStatus(DocumentStatus.GOVERNANCE_APPLIED);
                }
                documentService.save(enforced);
            }

            statusNotifier.emitLog(doc.getId(), "", "ENFORCEMENT", "INFO",
                    "Governance applied", System.currentTimeMillis() - start);
            log.info("[Engine] Governance applied for document: {}", doc.getId());
        } catch (Exception e) {
            log.error("[Engine] Governance enforcement failed for {}: {}", doc.getId(), e.getMessage(), e);
            statusNotifier.emitLog(doc.getId(), "", "ENFORCEMENT", "ERROR",
                    "Enforcement failed: " + e.getMessage(), System.currentTimeMillis() - start);
            throw new PipelineStageException("ENFORCEMENT", e);
        }
    }

    /**
     * Phase 1.10 PR3 — dispatch enforcement either to the HTTP worker
     * (when {@code pipeline.enforcement-worker.enabled=true}) or to
     * the in-process {@link EnforcementService} (default). Both paths
     * persist the document to Mongo internally; this method returns
     * the freshly-fetched {@link DocumentModel} so the caller can
     * apply per-node toggle clearing + status routing on top.
     */
    private DocumentModel applyEnforcement(DocumentModel doc, DocumentClassifiedEvent event, VisualNode node) {
        EnforcementWorkerClient client = enforcementWorkerClientProvider.getIfAvailable();
        if (client == null) {
            return enforcementService.enforce(event);
        }
        String nodeRunId = node != null && node.id() != null && !node.id().isBlank()
                ? "enforce-" + doc.getId() + "-" + node.id()
                : "enforce-" + doc.getId() + "-" + System.currentTimeMillis();
        EnforcementWorkerClient.Outcome outcome = client.enforce(nodeRunId, event);
        log.info("[Engine] enforcement-worker outcome doc={} migrated={} tierBefore={} tierAfter={} durationMs={}",
                doc.getId(), outcome.storageMigrated(),
                outcome.storageTierBefore(), outcome.storageTierAfter(), outcome.durationMs());
        return documentService.getById(doc.getId());
    }

    private void handleHumanReview(DocumentModel doc, DocumentClassifiedEvent event, VisualNode node) {
        doc.setCategoryId(event.categoryId());
        doc.setCategoryName(event.categoryName());
        doc.setSensitivityLabel(event.sensitivityLabel());
        doc.setTags(event.tags());
        doc.setClassificationResultId(event.classificationResultId());
        doc.setClassifiedAt(event.classifiedAt());
        doc.setStatus(DocumentStatus.REVIEW_REQUIRED);
        documentService.save(doc);

        statusNotifier.emitLog(doc.getId(), "", "ENFORCEMENT", "INFO",
                "Routed to human review queue", null);
        log.info("[Engine] Document {} routed to human review (category: {}, confidence: {})",
                doc.getId(), event.categoryName(), event.confidence());
    }

    private void handleNotification(DocumentModel doc, DocumentClassifiedEvent event, VisualNode node) {
        Map<String, Object> data = node.data() != null ? node.data() : Map.of();
        String message = data.getOrDefault("message", "Pipeline complete").toString();
        log.info("[Engine] Notification for document {}: {}", doc.getId(), message);
        statusNotifier.emitLog(doc.getId(), "", "NOTIFICATION", "INFO", message, null);
    }

    private void mergeCustomResult(DocumentModel doc, Map<String, Object> customResult, VisualNode node) {
        if (customResult == null || customResult.isEmpty()) return;
        log.info("[Engine] Storing custom LLM result for node '{}' on doc {}: {}", node.label(), doc.getId(), customResult.keySet());
        documentService.save(doc);
    }

    // ── Data-driven dispatch helpers ─────────────────────────────────────

    private void executePhase2Node(DocumentModel doc, DocumentClassifiedEvent event,
                                    VisualNode node, String nodeType,
                                    PipelineDefinition pipeline, List<VisualNode> executionOrder) {
        NodeTypeDefinition typeDef = nodeTypeService.getByKey(nodeType).orElse(null);
        String execCategory = typeDef != null ? typeDef.getExecutionCategory() : null;
        if (execCategory == null) {
            log.debug("[Engine] Unknown post-classification node type: {} — skipping", nodeType);
            return;
        }

        switch (execCategory) {
            case "NOOP" -> { }
            case "BUILT_IN" -> {
                switch (nodeType) {
                    case "condition" -> {
                        String nextNodeId = evaluateCondition(doc, event, node, pipeline);
                        if (nextNodeId != null) executeFromNode(doc, event, pipeline, executionOrder, nextNodeId);
                    }
                    case "governance" -> handleGovernance(doc, event, node);
                    case "humanReview" -> handleHumanReview(doc, event, node);
                    case "notification" -> handleNotification(doc, event, node);
                    default -> handlerRegistry.getHandler(nodeType)
                            .ifPresent(h -> h.handle(new NodeHandlerContext(doc, node,
                                    buildMergedConfig(node), null, event)));
                }
            }
            default -> log.debug("[Engine] Unexpected execution category in Phase 2: {} for node {}", execCategory, nodeType);
        }
    }

    private Map<String, Object> buildMergedConfig(VisualNode node) {
        Map<String, Object> config = node.data() != null ? new HashMap<>(node.data()) : new HashMap<>();
        Map<String, Object> blockContent = loadBlockContent(node.blockId(), null);
        if (blockContent != null) config.putAll(blockContent);
        return config;
    }

    private DocumentClassifiedEvent applyAcceleratorResult(DocumentModel doc, AcceleratorResult accel) {
        DocumentClassificationResult result = new DocumentClassificationResult();
        result.setDocumentId(doc.getId());
        result.setCategoryId(accel.categoryId());
        result.setCategoryName(accel.categoryName());
        result.setSensitivityLabel(accel.sensitivityLabel());
        result.setTags(accel.tags());
        result.setRetentionScheduleId(accel.retentionScheduleId());
        result.setConfidence(accel.confidence());
        result.setReasoning(accel.reasoning());
        result.setModelId("accelerator:" + accel.acceleratorType());
        result.setClassifiedAt(Instant.now());
        result.setSummary("Classified by " + accel.acceleratorType() + " accelerator");
        DocumentClassificationResult saved = classificationResultRepo.save(result);

        doc.setClassificationResultId(saved.getId());
        doc.setCategoryId(accel.categoryId());
        doc.setCategoryName(accel.categoryName());
        doc.setSensitivityLabel(accel.sensitivityLabel());
        doc.setTags(accel.tags());
        doc.setStatus(DocumentStatus.CLASSIFIED);
        doc.setClassifiedAt(Instant.now());
        documentService.save(doc);

        statusNotifier.emitLog(doc.getId(), doc.getOriginalFileName() != null ? doc.getOriginalFileName() : "",
                "CLASSIFICATION", "INFO",
                "Accelerator classified: " + accel.categoryName() + " (confidence: "
                        + String.format("%.2f", accel.confidence()) + ")", null);

        return new DocumentClassifiedEvent(
                doc.getId(), saved.getId(),
                accel.categoryId(), accel.categoryName(),
                accel.sensitivityLabel(), accel.tags(),
                List.of(), accel.retentionScheduleId(),
                accel.confidence(), false, Instant.now()
        );
    }

    private void handleSmartTruncation(DocumentModel doc, VisualNode node) {
        Map<String, Object> nodeConfig = node.data() != null ? new HashMap<>(node.data()) : new HashMap<>();
        Map<String, Object> blockContent = loadBlockContent(node.blockId(), null);
        if (blockContent != null) nodeConfig.putAll(blockContent);

        long start = System.currentTimeMillis();
        String truncated = smartTruncationService.truncate(doc, nodeConfig);
        long ms = System.currentTimeMillis() - start;

        int originalLen = doc.getExtractedText() != null ? doc.getExtractedText().length() : 0;
        doc.setExtractedText(truncated);
        documentService.save(doc);

        statusNotifier.emitLog(doc.getId(), doc.getOriginalFileName() != null ? doc.getOriginalFileName() : "",
                "ACCELERATOR", "INFO",
                "Smart truncation: " + originalLen + " → " + (truncated != null ? truncated.length() : 0) + " chars",
                ms);
    }

    // ── Graph utilities ─────────────────────────────────────────────────

    private PipelineDefinition resolvePipeline(DocumentModel doc) {
        // If a specific pipeline is set (manual selection or run-pipeline), use it
        if (doc.getPipelineId() != null) {
            var byId = pipelineRoutingService.findById(doc.getPipelineId());
            if (byId.isPresent() && byId.get().isActive()) {
                log.info("[Engine] Using explicitly set pipeline '{}' for doc {}", byId.get().getName(), doc.getId());
                return byId.get();
            }
            log.warn("[Engine] Pipeline {} not found or inactive — falling back to auto-routing", doc.getPipelineId());
        }
        // Auto-route based on category + mime type
        return pipelineRoutingService.resolve(doc.getCategoryId(), doc.getMimeType());
    }

    List<VisualNode> topologicalSort(PipelineDefinition pipeline) {
        if (pipeline == null || pipeline.getVisualNodes() == null || pipeline.getVisualNodes().isEmpty()) {
            return List.of();
        }

        List<VisualNode> nodes = pipeline.getVisualNodes();
        List<VisualEdge> edges = pipeline.getVisualEdges() != null ? pipeline.getVisualEdges() : List.of();

        Map<String, VisualNode> nodeMap = new LinkedHashMap<>();
        Map<String, List<String>> adj = new LinkedHashMap<>();
        Map<String, Integer> inDegree = new LinkedHashMap<>();

        for (VisualNode n : nodes) {
            nodeMap.put(n.id(), n);
            adj.put(n.id(), new ArrayList<>());
            inDegree.put(n.id(), 0);
        }

        for (VisualEdge e : edges) {
            // Include ALL edges for topological ordering (including error handles)
            // so that error-target nodes are correctly placed after their source.
            // Error handles are only skipped during branch *execution*, not ordering.
            if (adj.containsKey(e.source()) && nodeMap.containsKey(e.target())) {
                adj.get(e.source()).add(e.target());
                inDegree.merge(e.target(), 1, Integer::sum);
            }
        }

        Queue<String> queue = new LinkedList<>();
        for (var entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) queue.add(entry.getKey());
        }

        List<VisualNode> sorted = new ArrayList<>();
        while (!queue.isEmpty()) {
            String current = queue.poll();
            VisualNode node = nodeMap.get(current);
            if (node != null) sorted.add(node);

            for (String neighbor : adj.getOrDefault(current, List.of())) {
                int newDegree = inDegree.get(neighbor) - 1;
                inDegree.put(neighbor, newDegree);
                if (newDegree == 0) queue.add(neighbor);
            }
        }

        return sorted;
    }

    private void executeFromNode(DocumentModel doc, DocumentClassifiedEvent event,
                                 PipelineDefinition pipeline, List<VisualNode> executionOrder,
                                 String startNodeId) {
        Set<String> reachable = new LinkedHashSet<>();
        Queue<String> bfs = new LinkedList<>();
        bfs.add(startNodeId);
        Map<String, List<String>> adj = buildAdjacency(pipeline);

        while (!bfs.isEmpty()) {
            String id = bfs.poll();
            if (reachable.add(id)) {
                for (String neighbor : adj.getOrDefault(id, List.of())) bfs.add(neighbor);
            }
        }

        for (VisualNode node : executionOrder) {
            if (!reachable.contains(node.id())) continue;
            if (isNodeDisabled(node)) continue;

            doc.setPipelineNodeId(node.id());
            documentService.save(doc);

            String nodeType = node.type();
            executePhase2Node(doc, event, node, nodeType, pipeline, executionOrder);

            NodeTypeDefinition typeDef = nodeTypeService.getByKey(nodeType).orElse(null);
            if (typeDef != null && typeDef.isRequiresDocReload()) {
                doc = documentService.getById(doc.getId());
                if (doc == null) {
                    log.error("[Engine] Document {} disappeared after {} handler", event.documentId(), nodeType);
                    return;
                }
            }
        }

        doc.setPipelineNodeId(null);
        documentService.save(doc);
    }

    private Map<String, List<String>> buildAdjacency(PipelineDefinition pipeline) {
        Map<String, List<String>> adj = new LinkedHashMap<>();
        if (pipeline == null || pipeline.getVisualEdges() == null) return adj;
        for (VisualEdge e : pipeline.getVisualEdges()) {
            if ("error".equals(e.sourceHandle()) || "errorInput".equals(e.targetHandle())) continue;
            adj.computeIfAbsent(e.source(), k -> new ArrayList<>()).add(e.target());
        }
        return adj;
    }

    private boolean isNodeDisabled(VisualNode node) {
        if (node.data() == null) return false;
        Object disabled = node.data().get("disabled");
        return Boolean.TRUE.equals(disabled) || "true".equals(disabled);
    }

    private Map<String, Object> loadBlockContent(String blockId, PipelineBlock.BlockType fallbackType) {
        if (blockId != null) {
            return blockRepo.findById(blockId)
                    .map(PipelineBlock::getActiveContent)
                    .orElse(null);
        }
        List<PipelineBlock> blocks = blockRepo.findByTypeAndActiveTrueOrderByNameAsc(fallbackType);
        if (!blocks.isEmpty()) return blocks.getFirst().getActiveContent();
        return null;
    }

    private void handleNodeError(String docId, String fileName, String stage, Exception e,
                                 VisualNode errorHandlerNode) {
        String resolvedStage = stage;
        Throwable cause = e;
        if (e instanceof PipelineStageException pse) {
            resolvedStage = pse.getStage();
            cause = pse.getCause() != null ? pse.getCause() : e;
        }

        if (errorHandlerNode != null) {
            Map<String, Object> data = errorHandlerNode.data() != null ? errorHandlerNode.data() : Map.of();
            int maxRetries = toInt(data.get("maxRetries"), 3);
            DocumentModel doc = documentService.getById(docId);
            if (doc != null && doc.getRetryCount() < maxRetries) {
                log.info("[Engine] Error handler: retry {}/{} for document {}",
                        doc.getRetryCount() + 1, maxRetries, docId);
            }
        }

        DocumentStatus failedStatus = switch (resolvedStage) {
            case "EXTRACTION", "PII_SCAN", "PHASE_1" -> DocumentStatus.PROCESSING_FAILED;
            case "CLASSIFICATION" -> DocumentStatus.CLASSIFICATION_FAILED;
            case "ENFORCEMENT", "PHASE_2" -> DocumentStatus.ENFORCEMENT_FAILED;
            default -> DocumentStatus.PROCESSING_FAILED;
        };

        String errorMessage = cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
        String stackSnippet = truncateStack(cause, 500);
        String fullError = errorMessage + (stackSnippet.isEmpty() ? "" : "\n--- Stack ---\n" + stackSnippet);

        try {
            documentService.setError(docId, failedStatus, resolvedStage, fullError);
        } catch (Exception inner) {
            log.error("[Engine] Failed to set error status for {}: {}", docId, inner.getMessage());
            try {
                var sysError = co.uk.wolfnotsheep.document.models.SystemError.of(
                        "CRITICAL", "PIPELINE",
                        "Pipeline " + resolvedStage + " failed for document " + docId + ": " + errorMessage);
                sysError.setDocumentId(docId);
                sysError.setService("api");
                sysError.setStackTrace(stackSnippet);
                systemErrorRepo.save(sysError);
            } catch (Exception sysErr) {
                log.error("[Engine] Failed to persist SystemError for {}: {}", docId, sysErr.getMessage());
            }
        }
    }

    private static String truncateStack(Throwable t, int maxLength) {
        var sw = new java.io.StringWriter();
        t.printStackTrace(new java.io.PrintWriter(sw));
        String full = sw.toString();
        return full.length() <= maxLength ? full : full.substring(0, maxLength) + "...";
    }

    static class PipelineStageException extends RuntimeException {
        private final String stage;
        PipelineStageException(String stage, Throwable cause) { super(cause); this.stage = stage; }
        String getStage() { return stage; }
    }

    // ── Type conversion helpers ─────────────────────────────────────────

    private static int toInt(Object val, int defaultVal) {
        if (val instanceof Number n) return n.intValue();
        if (val instanceof String s) { try { return Integer.parseInt(s); } catch (Exception e) { return defaultVal; } }
        return defaultVal;
    }

    private static boolean toBool(Object val, boolean defaultVal) {
        if (val instanceof Boolean b) return b;
        if (val instanceof String s) return Boolean.parseBoolean(s);
        return defaultVal;
    }

    private static double toDouble(Object val, double defaultVal) {
        if (val instanceof Number n) return n.doubleValue();
        if (val instanceof String s) { try { return Double.parseDouble(s); } catch (Exception e) { return defaultVal; } }
        return defaultVal;
    }
}
