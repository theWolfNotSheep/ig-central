package co.uk.wolfnotsheep.infrastructure.services.pipeline;

import co.uk.wolfnotsheep.docprocessing.extraction.TextExtractionService;
import co.uk.wolfnotsheep.document.events.DocumentClassifiedEvent;
import co.uk.wolfnotsheep.document.events.DocumentIngestedEvent;
import co.uk.wolfnotsheep.document.events.DocumentProcessedEvent;
import co.uk.wolfnotsheep.document.models.DocumentModel;
import co.uk.wolfnotsheep.document.models.DocumentStatus;
import co.uk.wolfnotsheep.document.models.PiiEntity;
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
import co.uk.wolfnotsheep.infrastructure.services.pipeline.accelerators.AcceleratorHandler;
import co.uk.wolfnotsheep.infrastructure.services.pipeline.accelerators.AcceleratorResult;
import co.uk.wolfnotsheep.infrastructure.services.pipeline.accelerators.SmartTruncationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.Instant;
import java.util.*;

/**
 * Pipeline execution engine that walks the visual graph defined in PipelineDefinition.
 * Replaces the hardcoded 3-consumer pipeline with a graph-driven approach.
 *
 * Execution is split into two phases separated by the async LLM boundary:
 * <ul>
 *   <li>Phase 1: trigger → textExtraction → piiScanner → aiClassification (publish to RabbitMQ, stop)</li>
 *   <li>Phase 2: (after LLM responds) condition → governance/humanReview → notification</li>
 * </ul>
 *
 * The document's pipelineNodeId field tracks the current position in the graph.
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
                                   co.uk.wolfnotsheep.document.repositories.SystemErrorRepository systemErrorRepo) {
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
    }

    // ── Unified execution: walks the entire graph in a single pass ──────

    /**
     * Execute the entire pipeline in a single pass — no Phase 1/Phase 2 split.
     * When an LLM node is encountered, it's called synchronously via the llm-worker REST API.
     * Multiple LLM nodes are supported — each executes inline and the engine continues.
     */
    public void executePipeline(DocumentIngestedEvent event) {
        String docId = event.documentId();
        log.info("[Engine] Unified pipeline starting for document: {} ({})", docId, event.fileName());

        try {
            DocumentModel doc = documentService.getById(docId);
            if (doc == null) { log.warn("[Engine] Document {} not found — skipping", docId); return; }
            if (doc.getCancelledAt() != null && event.ingestedAt() != null
                    && doc.getCancelledAt().isAfter(event.ingestedAt())) {
                log.info("[Engine] Document {} cancelled — skipping", docId); return;
            }
            if (doc.getStatus() != co.uk.wolfnotsheep.document.models.DocumentStatus.UPLOADED) {
                log.info("[Engine] Document {} status is {} (expected UPLOADED) — skipping", docId, doc.getStatus()); return;
            }

            PipelineDefinition pipeline = resolvePipeline(doc);
            if (pipeline != null) {
                doc.setPipelineId(pipeline.getId());
                documentService.save(doc);
            }

            List<VisualNode> executionOrder = topologicalSort(pipeline);
            log.info("[Engine] Pipeline '{}' has {} nodes — running unified execution",
                    pipeline != null ? pipeline.getName() : "default", executionOrder.size());

            // Track the most recent classification result for post-classification nodes
            DocumentClassifiedEvent currentClassification = null;

            for (VisualNode node : executionOrder) {
                if (isNodeDisabled(node)) continue;

                String nodeType = node.type();
                doc.setPipelineNodeId(node.id());
                documentService.save(doc);

                NodeTypeDefinition typeDef = nodeTypeService.getByKey(nodeType).orElse(null);
                String execCategory = typeDef != null ? typeDef.getExecutionCategory() : null;

                if (execCategory == null) {
                    log.debug("[Engine] Unknown node type: {} — skipping", nodeType);
                    continue;
                }

                final DocumentModel currentDoc = doc;
                final DocumentClassifiedEvent currentClassRef = currentClassification;

                switch (execCategory) {
                    case "NOOP" -> { /* trigger, errorHandler */ }

                    case "BUILT_IN" -> {
                        switch (nodeType) {
                            // Pre-classification built-ins
                            case "textExtraction" -> handleTextExtraction(doc, event, node);
                            case "piiScanner" -> handlePiiScan(doc, event, node);
                            case "smartTruncation" -> handleSmartTruncation(doc, node);
                            // Post-classification built-ins (need currentClassification context)
                            case "condition" -> {
                                if (currentClassification != null) {
                                    String nextNodeId = evaluateCondition(doc, currentClassification, node, pipeline);
                                    if (nextNodeId != null) {
                                        executeFromNodeUnified(doc, currentClassification, event, pipeline, executionOrder, nextNodeId);
                                        // After branch execution completes, the pipeline is done
                                        doc.setPipelineNodeId(null);
                                        documentService.save(doc);
                                        log.info("[Engine] Unified pipeline complete for document: {}", docId);
                                        return;
                                    }
                                } else {
                                    log.warn("[Engine] Condition node '{}' encountered before any classification — skipping", node.label());
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
                            log.info("[Engine] Accelerator '{}' short-circuited LLM for doc {} → {}",
                                    nodeType, docId, accel.categoryName());
                            // Don't return — continue walking the graph
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
                            currentClassification = applyAcceleratorResult(doc, httpResult);
                        }
                    }

                    case "SYNC_LLM" -> {
                        Map<String, Object> mergedConfig = buildMergedConfig(node);
                        // Pass blockId from the node config
                        if (node.blockId() != null) mergedConfig.put("blockId", node.blockId());

                        statusNotifier.emitLog(doc.getId(),
                                doc.getOriginalFileName() != null ? doc.getOriginalFileName() : "",
                                "LLM_CALL", "INFO",
                                "Calling LLM for node '" + node.label() + "'", null);

                        long llmStart = System.currentTimeMillis();
                        SyncLlmNodeExecutor.SyncLlmResult llmResult = syncLlmExecutor.execute(doc, mergedConfig, nodeType, event);
                        long llmMs = System.currentTimeMillis() - llmStart;

                        if (llmResult.success()) {
                            if (llmResult.classificationEvent() != null) {
                                // Classification mode — update tracking context
                                currentClassification = llmResult.classificationEvent();
                                // Apply classification fields to document
                                doc.setCategoryId(currentClassification.categoryId());
                                doc.setCategoryName(currentClassification.categoryName());
                                doc.setSensitivityLabel(currentClassification.sensitivityLabel());
                                doc.setClassificationResultId(currentClassification.classificationResultId());
                                doc.setClassifiedAt(java.time.Instant.now());
                                documentService.save(doc);

                                statusNotifier.emitLog(doc.getId(), doc.getOriginalFileName() != null ? doc.getOriginalFileName() : "",
                                        "LLM_CALL", "INFO",
                                        "Classified as " + currentClassification.categoryName() + " (" + currentClassification.confidence() + ")",
                                        llmMs);
                            }
                            if (llmResult.customResult() != null) {
                                // Custom prompt mode — merge into document metadata
                                mergeCustomResult(doc, llmResult.customResult(), node);
                                statusNotifier.emitLog(doc.getId(), doc.getOriginalFileName() != null ? doc.getOriginalFileName() : "",
                                        "LLM_CALL", "INFO",
                                        "Custom prompt complete for '" + node.label() + "'", llmMs);
                            }
                        } else {
                            statusNotifier.emitLog(doc.getId(), doc.getOriginalFileName() != null ? doc.getOriginalFileName() : "",
                                    "LLM_CALL", "ERROR",
                                    "LLM call failed: " + llmResult.error(), llmMs);
                            log.error("[Engine] SYNC_LLM failed for doc {}: {}", docId, llmResult.error());
                            // Continue pipeline — don't fail the whole thing for one LLM error
                        }
                    }

                    case "ASYNC_BOUNDARY" -> {
                        // Fallback for un-migrated definitions — old behavior
                        log.info("[Engine] ASYNC_BOUNDARY fallback — publishing to RabbitMQ for doc {}", docId);
                        handleAiClassification(doc, event, node);
                        return; // stop and wait for async response
                    }

                    default -> log.debug("[Engine] Unknown execution category: {} for node {}", execCategory, nodeType);
                }

                // Data-driven document reload
                if (typeDef != null && typeDef.isRequiresDocReload()) {
                    doc = documentService.getById(docId);
                    if (doc == null) {
                        log.error("[Engine] Document {} disappeared after {} handler", docId, nodeType);
                        return;
                    }
                }
            }

            // Pipeline complete
            doc.setPipelineNodeId(null);
            documentService.save(doc);
            log.info("[Engine] Unified pipeline complete for document: {}", docId);

        } catch (Exception e) {
            log.error("[Engine] Pipeline failed for document {}: {}", docId, e.getMessage(), e);
            handleNodeError(docId, event.fileName(), "PIPELINE", e, null);
        }
    }

    /**
     * Merge custom LLM result into document's extracted metadata.
     */
    private void mergeCustomResult(DocumentModel doc, Map<String, Object> customResult, VisualNode node) {
        // Store under the node label as a key in extractedMetadata-style map
        // For now, store as top-level fields on the document's metadata
        if (customResult == null || customResult.isEmpty()) return;
        log.info("[Engine] Storing custom LLM result for node '{}' on doc {}: {}", node.label(), doc.getId(), customResult.keySet());
        // TODO: store in a structured metadata map on the document when the field exists
        documentService.save(doc);
    }

    /**
     * Execute from a specific node in the unified pipeline (for condition branches).
     */
    private void executeFromNodeUnified(DocumentModel doc, DocumentClassifiedEvent classification,
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

            switch (execCategory) {
                case "NOOP" -> { }
                case "BUILT_IN" -> {
                    switch (nodeType) {
                        case "condition" -> {
                            String nextNodeId = evaluateCondition(doc, classification, node, pipeline);
                            if (nextNodeId != null) {
                                executeFromNodeUnified(doc, classification, ingestedEvent, pipeline, executionOrder, nextNodeId);
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
                    Map<String, Object> mergedConfig = buildMergedConfig(node);
                    if (node.blockId() != null) mergedConfig.put("blockId", node.blockId());
                    SyncLlmNodeExecutor.SyncLlmResult llmResult = syncLlmExecutor.execute(doc, mergedConfig, nodeType, ingestedEvent);
                    if (llmResult.success() && llmResult.customResult() != null) {
                        mergeCustomResult(doc, llmResult.customResult(), node);
                    }
                }
                default -> log.debug("[Engine] Skipping {} node type in branch: {}", execCategory, nodeType);
            }

            if (typeDef != null && typeDef.isRequiresDocReload()) {
                doc = documentService.getById(doc.getId());
                if (doc == null) return;
            }
        }
    }

    // ── Phase 1: document.ingested → run until aiClassification ─────────

    /**
     * Execute Phase 1 of the pipeline: everything before the LLM call.
     * Called when a document is ingested.
     *
     * @return Phase1Result indicating whether the LLM was skipped (accelerator short-circuit)
     *         or the pipeline is awaiting the LLM response.
     */
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

            // Resolve pipeline
            PipelineDefinition pipeline = resolvePipeline(doc);
            if (pipeline != null) {
                doc.setPipelineId(pipeline.getId());
                documentService.save(doc);
            }

            // Build execution graph
            List<VisualNode> executionOrder = topologicalSort(pipeline);
            log.info("[Engine] Pipeline '{}' has {} nodes", pipeline != null ? pipeline.getName() : "default", executionOrder.size());

            // Walk the graph until we hit aiClassification or run out of nodes
            for (VisualNode node : executionOrder) {
                if (isNodeDisabled(node)) {
                    log.debug("[Engine] Skipping disabled node: {} ({})", node.label(), node.type());
                    continue;
                }

                String nodeType = node.type();
                doc.setPipelineNodeId(node.id());
                documentService.save(doc);

                // Data-driven dispatch based on executionCategory from NodeTypeDefinition
                NodeTypeDefinition typeDef = nodeTypeService.getByKey(nodeType).orElse(null);
                String execCategory = typeDef != null ? typeDef.getExecutionCategory() : null;

                if (execCategory == null) {
                    log.debug("[Engine] Unknown pre-classification node type: {} — skipping", nodeType);
                } else {
                    final DocumentModel currentDoc = doc; // capture for lambda
                    switch (execCategory) {
                        case "NOOP" -> { /* trigger, errorHandler — no-op during normal flow */ }
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
                            long accelMs = System.currentTimeMillis() - System.currentTimeMillis();
                            statusNotifier.emitLog(doc.getId(),
                                    doc.getOriginalFileName() != null ? doc.getOriginalFileName() : "",
                                    "ACCELERATOR", "INFO",
                                    nodeType + (accel.matched() ? " → HIT: " + accel.categoryName() : " → MISS"), null);
                            if (accel.matched()) {
                                DocumentClassifiedEvent classifiedEvent = applyAcceleratorResult(doc, accel);
                                log.info("[Engine] Accelerator '{}' short-circuited LLM for doc {} → {}",
                                        nodeType, docId, accel.categoryName());
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
                                DocumentClassifiedEvent classifiedEvent = applyAcceleratorResult(doc, httpResult);
                                log.info("[Engine] Generic HTTP '{}' short-circuited LLM for doc {} → {}",
                                        nodeType, docId, httpResult.categoryName());
                                return Phase1Result.shortCircuited(classifiedEvent);
                            }
                        }
                        case "ASYNC_BOUNDARY" -> {
                            handleAiClassification(doc, event, node);
                            return Phase1Result.awaitingLlm();
                        }
                        default -> log.debug("[Engine] Unknown execution category: {} for node {}", execCategory, nodeType);
                    }
                }

                // Data-driven document reload
                if (typeDef != null && typeDef.isRequiresDocReload()) {
                    doc = documentService.getById(docId);
                    if (doc == null) {
                        log.error("[Engine] Document {} disappeared after {} handler", docId, nodeType);
                        return Phase1Result.awaitingLlm();
                    }
                }
            }

            // If no aiClassification node, the pipeline ends after extraction/PII
            log.info("[Engine] Phase 1 complete — no aiClassification node found in pipeline");
            return Phase1Result.awaitingLlm();

        } catch (Exception e) {
            log.error("[Engine] Phase 1 failed for document {}: {}", docId, e.getMessage(), e);
            handleNodeError(docId, event.fileName(), "PHASE_1", e, null);
            return Phase1Result.awaitingLlm();
        }
    }

    // ── Phase 2: document.classified → run remaining nodes ──────────────

    /**
     * Execute Phase 2 of the pipeline: everything after the LLM classification.
     * Called when a classified event is received.
     */
    public void executePhase2(DocumentClassifiedEvent event) {
        String docId = event.documentId();
        log.info("[Engine] Phase 2 starting for document: {} (category: {})", docId, event.categoryName());

        try {
            DocumentModel doc = documentService.getById(docId);
            if (doc == null) {
                log.warn("[Engine] Skipping Phase 2 for {} — not found", docId);
                return;
            }
            if (doc.getStatus() != DocumentStatus.CLASSIFIED
                    && doc.getStatus() != DocumentStatus.REVIEW_REQUIRED) {
                log.info("[Engine] Skipping Phase 2 for {} — status is {} (expected CLASSIFIED or REVIEW_REQUIRED)",
                        docId, doc.getStatus());
                return;
            }

            PipelineDefinition pipeline = null;
            if (doc.getPipelineId() != null) {
                pipeline = pipelineRoutingService.resolve(event.categoryId(), null);
                // Also try the saved pipeline
                if (pipeline == null) {
                    var repo = blockRepo; // just for context - we use pipelineRoutingService
                }
            }
            if (pipeline == null) {
                pipeline = resolvePipeline(doc);
            }

            List<VisualNode> executionOrder = topologicalSort(pipeline);

            // Find the node AFTER the async boundary (aiClassification)
            boolean pastClassification = false;
            for (VisualNode node : executionOrder) {
                if (!pastClassification) {
                    NodeTypeDefinition boundaryCheck = nodeTypeService.getByKey(node.type()).orElse(null);
                    if (boundaryCheck != null && "ASYNC_BOUNDARY".equals(boundaryCheck.getExecutionCategory())) {
                        pastClassification = true;
                    }
                    continue;
                }

                if (isNodeDisabled(node)) {
                    log.debug("[Engine] Skipping disabled node: {} ({})", node.label(), node.type());
                    continue;
                }

                String nodeType = node.type();
                doc.setPipelineNodeId(node.id());
                documentService.save(doc);

                executePhase2Node(doc, event, node, nodeType, pipeline, executionOrder);

                // Data-driven document reload
                NodeTypeDefinition typeDef = nodeTypeService.getByKey(nodeType).orElse(null);
                if (typeDef != null && typeDef.isRequiresDocReload()) {
                    doc = documentService.getById(docId);
                    if (doc == null) {
                        log.error("[Engine] Document {} disappeared after {} handler", docId, nodeType);
                        return;
                    }
                }
            }

            // Pipeline complete — clear the node tracker
            doc.setPipelineNodeId(null);
            documentService.save(doc);
            log.info("[Engine] Phase 2 complete for document: {}", docId);

        } catch (Exception e) {
            log.error("[Engine] Phase 2 failed for document {}: {}", docId, e.getMessage(), e);
            handleNodeError(docId, "", "PHASE_2", e, null);
        }
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
            // Load EXTRACTOR block config
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

            // Download and extract
            InputStream fileStream = objectStorage.download(event.storageBucket(), event.storageKey());
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
            // Build dismissal context
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
        // Publish to the LLM queue and return — the LLM worker handles classification asynchronously
        var processedEvent = new DocumentProcessedEvent(
                doc.getId(),
                event.fileName(),
                event.mimeType(),
                event.fileSizeBytes(),
                doc.getExtractedText(),
                event.storageBucket() + "/" + event.storageKey(),
                event.uploadedBy(),
                Instant.now()
        );

        try {
            rabbitTemplate.convertAndSend(
                    RabbitMqConfig.EXCHANGE,
                    RabbitMqConfig.ROUTING_PROCESSED,
                    processedEvent
            );
            log.info("[Engine] Published to LLM queue for document: {} — pausing pipeline at node {}",
                    doc.getId(), node.id());
        } catch (Exception e) {
            log.error("[Engine] Failed to publish to LLM queue for {}: {}", doc.getId(), e.getMessage());
            documentService.setError(doc.getId(), DocumentStatus.PROCESSING_FAILED,
                    "QUEUE", "Failed to publish to classification queue: " + e.getMessage());
        }
    }

    private String evaluateCondition(DocumentModel doc, DocumentClassifiedEvent event,
                                     VisualNode node, PipelineDefinition pipeline) {
        // Read condition config from node data or linked block
        Map<String, Object> data = node.data() != null ? new HashMap<>(node.data()) : new HashMap<>();
        Map<String, Object> blockContent = loadBlockContent(node.blockId(), PipelineBlock.BlockType.ROUTER);
        if (blockContent != null) {
            data.putAll(blockContent);
        }

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

        // Find the target node for the chosen branch
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
        long start = System.currentTimeMillis();
        statusNotifier.emitLog(doc.getId(), "", "ENFORCEMENT", "INFO",
                "Applying governance: " + event.categoryName() + " / " + event.sensitivityLabel(), null);

        try {
            DocumentModel enforced = enforcementService.enforce(event);

            // Engine owns the status decision — the graph's condition/humanReview nodes handle review routing.
            // If this governance node runs, the document is on the auto-approve path.
            if (enforced != null) {
                enforced.setStatus(DocumentStatus.INBOX);
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

    private void handleHumanReview(DocumentModel doc, DocumentClassifiedEvent event, VisualNode node) {
        // Copy classification fields so the document is displayable in the review queue
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

    // ── Data-driven dispatch helpers ─────────────────────────────────────

    /**
     * Execute a single Phase 2 (post-classification) node using data-driven dispatch.
     */
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
            case "NOOP" -> { /* errorHandler — only triggered on failure */ }
            case "BUILT_IN" -> {
                switch (nodeType) {
                    case "condition" -> {
                        String nextNodeId = evaluateCondition(doc, event, node, pipeline);
                        if (nextNodeId != null) {
                            executeFromNode(doc, event, pipeline, executionOrder, nextNodeId);
                        }
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

    /**
     * Build merged config from node data + linked block content.
     */
    private Map<String, Object> buildMergedConfig(VisualNode node) {
        Map<String, Object> config = node.data() != null ? new HashMap<>(node.data()) : new HashMap<>();
        Map<String, Object> blockContent = loadBlockContent(node.blockId(), null);
        if (blockContent != null) config.putAll(blockContent);
        return config;
    }

    /**
     * Apply an accelerator's classification result to the document, creating a
     * ClassificationResult record and a synthetic DocumentClassifiedEvent.
     */
    private DocumentClassifiedEvent applyAcceleratorResult(DocumentModel doc, AcceleratorResult accel) {
        // Create classification result record
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

        // Update document fields
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

    /**
     * Smart truncation: reduces extracted text before the LLM node.
     * Does NOT produce a classification — it modifies doc.extractedText in place.
     */
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

    /**
     * Resolve which pipeline should be used for this document.
     */
    private PipelineDefinition resolvePipeline(DocumentModel doc) {
        if (doc.getPipelineId() != null) {
            PipelineDefinition p = pipelineRoutingService.resolve(doc.getCategoryId(), doc.getMimeType());
            if (p != null) return p;
        }
        return pipelineRoutingService.resolve(null, doc.getMimeType());
    }

    /**
     * Topologically sort the visual nodes following edges.
     * Returns nodes in execution order: sources before targets.
     */
    List<VisualNode> topologicalSort(PipelineDefinition pipeline) {
        if (pipeline == null || pipeline.getVisualNodes() == null || pipeline.getVisualNodes().isEmpty()) {
            return List.of();
        }

        List<VisualNode> nodes = pipeline.getVisualNodes();
        List<VisualEdge> edges = pipeline.getVisualEdges() != null ? pipeline.getVisualEdges() : List.of();

        // Build adjacency and in-degree
        Map<String, VisualNode> nodeMap = new LinkedHashMap<>();
        Map<String, List<String>> adj = new LinkedHashMap<>();
        Map<String, Integer> inDegree = new LinkedHashMap<>();

        for (VisualNode n : nodes) {
            nodeMap.put(n.id(), n);
            adj.put(n.id(), new ArrayList<>());
            inDegree.put(n.id(), 0);
        }

        for (VisualEdge e : edges) {
            // Skip error edges (connected to errorHandler nodes)
            if ("error".equals(e.sourceHandle()) || "errorInput".equals(e.targetHandle())) {
                continue;
            }
            if (adj.containsKey(e.source()) && nodeMap.containsKey(e.target())) {
                adj.get(e.source()).add(e.target());
                inDegree.merge(e.target(), 1, Integer::sum);
            }
        }

        // Kahn's algorithm
        Queue<String> queue = new LinkedList<>();
        for (var entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(entry.getKey());
            }
        }

        List<VisualNode> sorted = new ArrayList<>();
        while (!queue.isEmpty()) {
            String current = queue.poll();
            VisualNode node = nodeMap.get(current);
            if (node != null) sorted.add(node);

            for (String neighbor : adj.getOrDefault(current, List.of())) {
                int newDegree = inDegree.get(neighbor) - 1;
                inDegree.put(neighbor, newDegree);
                if (newDegree == 0) {
                    queue.add(neighbor);
                }
            }
        }

        return sorted;
    }

    /**
     * Continue execution from a specific node (used after condition branching).
     */
    private void executeFromNode(DocumentModel doc, DocumentClassifiedEvent event,
                                 PipelineDefinition pipeline, List<VisualNode> executionOrder,
                                 String startNodeId) {
        // Find all nodes reachable from startNodeId via BFS
        Set<String> reachable = new LinkedHashSet<>();
        Queue<String> bfs = new LinkedList<>();
        bfs.add(startNodeId);
        Map<String, List<String>> adj = buildAdjacency(pipeline);

        while (!bfs.isEmpty()) {
            String id = bfs.poll();
            if (reachable.add(id)) {
                for (String neighbor : adj.getOrDefault(id, List.of())) {
                    bfs.add(neighbor);
                }
            }
        }

        // Execute reachable nodes in topological order
        for (VisualNode node : executionOrder) {
            if (!reachable.contains(node.id())) continue;
            if (isNodeDisabled(node)) continue;

            doc.setPipelineNodeId(node.id());
            documentService.save(doc);

            String nodeType = node.type();
            executePhase2Node(doc, event, node, nodeType, pipeline, executionOrder);

            // Data-driven document reload
            NodeTypeDefinition typeDef = nodeTypeService.getByKey(nodeType).orElse(null);
            if (typeDef != null && typeDef.isRequiresDocReload()) {
                doc = documentService.getById(doc.getId());
                if (doc == null) {
                    log.error("[Engine] Document {} disappeared after {} handler", event.documentId(), nodeType);
                    return;
                }
            }
        }

        // Branch complete
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

    /**
     * Load block content by specific block ID, or fall back to the first active block of the given type.
     */
    private Map<String, Object> loadBlockContent(String blockId, PipelineBlock.BlockType fallbackType) {
        if (blockId != null) {
            return blockRepo.findById(blockId)
                    .map(PipelineBlock::getActiveContent)
                    .orElse(null);
        }
        List<PipelineBlock> blocks = blockRepo.findByTypeAndActiveTrueOrderByNameAsc(fallbackType);
        if (!blocks.isEmpty()) {
            return blocks.getFirst().getActiveContent();
        }
        return null;
    }

    private void handleNodeError(String docId, String fileName, String stage, Exception e,
                                 VisualNode errorHandlerNode) {
        // Extract the real stage if this is a PipelineStageException
        String resolvedStage = stage;
        Throwable cause = e;
        if (e instanceof PipelineStageException pse) {
            resolvedStage = pse.getStage();
            cause = pse.getCause() != null ? pse.getCause() : e;
        }

        // If there's an error handler node connected, use its config
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

        // Build error message with truncated stack trace for diagnostics
        String errorMessage = cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
        String stackSnippet = truncateStack(cause, 500);
        String fullError = errorMessage + (stackSnippet.isEmpty() ? "" : "\n--- Stack ---\n" + stackSnippet);

        try {
            documentService.setError(docId, failedStatus, resolvedStage, fullError);
        } catch (Exception inner) {
            log.error("[Engine] Failed to set error status for {}: {}", docId, inner.getMessage());
            // Fallback: persist as SystemError so it's visible in the admin panel
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

    /** Wraps a node handler exception with the pipeline stage name for better error reporting. */
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
