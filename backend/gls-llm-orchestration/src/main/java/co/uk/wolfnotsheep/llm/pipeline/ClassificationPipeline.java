package co.uk.wolfnotsheep.llm.pipeline;

import co.uk.wolfnotsheep.document.events.DocumentClassifiedEvent;
import co.uk.wolfnotsheep.document.events.DocumentProcessedEvent;
import co.uk.wolfnotsheep.document.events.LlmJobCompletedEvent;
import co.uk.wolfnotsheep.document.events.LlmJobRequestedEvent;
import co.uk.wolfnotsheep.document.models.DocumentModel;
import co.uk.wolfnotsheep.document.models.DocumentStatus;
import co.uk.wolfnotsheep.document.services.DocumentService;
import co.uk.wolfnotsheep.governance.models.DocumentClassificationResult;
import co.uk.wolfnotsheep.governance.models.PipelineBlock;
import co.uk.wolfnotsheep.governance.models.PipelineDefinition;
import co.uk.wolfnotsheep.governance.repositories.PipelineBlockRepository;
import co.uk.wolfnotsheep.governance.services.GovernanceService;
import co.uk.wolfnotsheep.governance.services.PipelineRoutingService;
import co.uk.wolfnotsheep.llm.config.LlmClientFactory;
import co.uk.wolfnotsheep.llm.config.RabbitMqConfig;
import co.uk.wolfnotsheep.llm.prompts.ClassificationPromptBuilder;
import co.uk.wolfnotsheep.platform.config.services.AppConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * The core classification pipeline. Consumes document.processed events,
 * drives LLM classification via Claude with MCP tools, and publishes
 * document.classified events.
 *
 * The MCP client auto-configuration provides ToolCallbackProvider beans
 * for each tool exposed by the MCP server. Spring AI's ChatClient
 * wires these as available tool calls for the Anthropic model.
 */
@Service
public class ClassificationPipeline {

    private static final Logger log = LoggerFactory.getLogger(ClassificationPipeline.class);

    private final LlmClientFactory llmClientFactory;
    private final ClassificationPromptBuilder promptBuilder;
    private final GovernanceService governanceService;
    private final DocumentService documentService;
    private final PipelineRoutingService pipelineRoutingService;
    private final RabbitTemplate rabbitTemplate;
    private final AppConfigService configService;
    private final PipelineBlockRepository blockRepo;
    private final co.uk.wolfnotsheep.document.services.PipelineStatusNotifier statusNotifier;
    private final co.uk.wolfnotsheep.document.repositories.AiUsageLogRepository aiUsageLogRepo;
    private final co.uk.wolfnotsheep.document.repositories.SystemErrorRepository systemErrorRepo;

    public ClassificationPipeline(LlmClientFactory llmClientFactory,
                                  ClassificationPromptBuilder promptBuilder,
                                  GovernanceService governanceService,
                                  DocumentService documentService,
                                  PipelineRoutingService pipelineRoutingService,
                                  RabbitTemplate rabbitTemplate,
                                  AppConfigService configService,
                                  PipelineBlockRepository blockRepo,
                                  co.uk.wolfnotsheep.document.services.PipelineStatusNotifier statusNotifier,
                                  co.uk.wolfnotsheep.document.repositories.AiUsageLogRepository aiUsageLogRepo,
                                  co.uk.wolfnotsheep.document.repositories.SystemErrorRepository systemErrorRepo) {
        this.llmClientFactory = llmClientFactory;
        this.promptBuilder = promptBuilder;
        this.statusNotifier = statusNotifier;
        this.aiUsageLogRepo = aiUsageLogRepo;
        this.systemErrorRepo = systemErrorRepo;
        this.governanceService = governanceService;
        this.documentService = documentService;
        this.pipelineRoutingService = pipelineRoutingService;
        this.rabbitTemplate = rabbitTemplate;
        this.configService = configService;
        this.blockRepo = blockRepo;
    }

    @RabbitListener(queues = RabbitMqConfig.QUEUE_PROCESSED)
    public void onDocumentProcessed(DocumentProcessedEvent event) {
        log.info("Received document for classification: {} ({})", event.documentId(), event.fileName());

        try {
            // Status guard: skip if document was cancelled or is no longer at PROCESSED
            DocumentModel current = documentService.getById(event.documentId());
            if (current == null || current.getStatus() != DocumentStatus.PROCESSED) {
                log.info("Skipping classification for {} — status is {} (expected PROCESSED)",
                        event.documentId(), current != null ? current.getStatus() : "NOT_FOUND");
                return;
            }

            documentService.updateStatus(event.documentId(), DocumentStatus.CLASSIFYING, "SYSTEM");
            statusNotifier.emitLog(event.documentId(), event.fileName(), "CLASSIFICATION", "INFO",
                    "Sending to " + llmClientFactory.getActiveProvider().toUpperCase() + " for classification", null);
            long classifyStart = System.currentTimeMillis();
            classify(event);
            long classifyMs = System.currentTimeMillis() - classifyStart;
            statusNotifier.emitLog(event.documentId(), event.fileName(), "CLASSIFICATION", "INFO",
                    "Classification complete", classifyMs);
        } catch (Exception e) {
            log.error("Classification failed for document {}: {}", event.documentId(), e.getMessage(), e);
            statusNotifier.emitLog(event.documentId(), event.fileName(), "CLASSIFICATION", "ERROR",
                    "Failed: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()), null);

            // Log failed AI usage
            try {
                var failLog = new co.uk.wolfnotsheep.document.models.AiUsageLog();
                failLog.setUsageType("CLASSIFY");
                failLog.setTriggeredBy(event.uploadedBy() != null ? event.uploadedBy() : "SYSTEM");
                failLog.setDocumentId(event.documentId());
                failLog.setDocumentName(event.fileName());
                failLog.setProvider(llmClientFactory.getActiveProvider());
                failLog.setModel(llmClientFactory.getActiveModel());
                failLog.setStatus("FAILED");
                failLog.setErrorMessage(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
                aiUsageLogRepo.save(failLog);
            } catch (Exception logErr) { log.error("Failed to save AI usage log: {}", logErr.getMessage()); }

            try {
                String errorDetail = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                documentService.setError(event.documentId(), DocumentStatus.CLASSIFICATION_FAILED,
                        "CLASSIFICATION", errorDetail);
            } catch (Exception inner) {
                log.error("Failed to set error status for {}: {}", event.documentId(), inner.getMessage());
                // Fallback: persist as SystemError so it's visible in admin
                try {
                    var sysError = co.uk.wolfnotsheep.document.models.SystemError.of(
                            "CRITICAL", "CLASSIFICATION",
                            "Classification failed for document " + event.documentId() + ": " + e.getMessage());
                    sysError.setDocumentId(event.documentId());
                    sysError.setService("llm-worker");
                    systemErrorRepo.save(sysError);
                } catch (Exception sysErr) {
                    log.error("Failed to persist SystemError for {}: {}", event.documentId(), sysErr.getMessage());
                }
            }
        }
    }

    /**
     * Synchronous classification entry point for the REST API.
     * Returns the classification result directly instead of publishing to RabbitMQ.
     * Called by ClassificationController for inline pipeline execution.
     */
    public co.uk.wolfnotsheep.llm.api.ClassifyResponse classifyInternal(DocumentProcessedEvent event) {
        return classifyInternal(event, java.util.Map.of());
    }

    /**
     * Synchronous classification with per-node LLM overrides.
     */
    public co.uk.wolfnotsheep.llm.api.ClassifyResponse classifyInternal(
            DocumentProcessedEvent event, java.util.Map<String, Object> llmOverrides) {
        try {
            DocumentModel doc = documentService.getById(event.documentId());
            if (doc == null) {
                return co.uk.wolfnotsheep.llm.api.ClassifyResponse.error("Document not found: " + event.documentId());
            }

            documentService.updateStatus(event.documentId(), DocumentStatus.CLASSIFYING, "SYSTEM");
            statusNotifier.emitLog(event.documentId(), event.fileName(), "CLASSIFICATION", "INFO",
                    "Sending to " + llmClientFactory.getActiveProvider().toUpperCase() + " for classification (sync)", null);

            long start = System.currentTimeMillis();
            classifyCore(event, llmOverrides);
            long ms = System.currentTimeMillis() - start;

            statusNotifier.emitLog(event.documentId(), event.fileName(), "CLASSIFICATION", "INFO",
                    "Classification complete (sync)", ms);

            // Retrieve the result saved by MCP tools
            var results = governanceService.getClassificationHistory(event.documentId());
            if (results.isEmpty()) {
                return co.uk.wolfnotsheep.llm.api.ClassifyResponse.error("LLM did not produce a classification result");
            }

            var latest = results.getFirst();
            double reviewThreshold = getReviewThreshold();
            boolean needsReview = latest.getConfidence() < reviewThreshold;

            return co.uk.wolfnotsheep.llm.api.ClassifyResponse.classification(
                    latest.getId(), latest.getCategoryId(), latest.getCategoryName(),
                    latest.getSensitivityLabel(), latest.getTags(), latest.getConfidence(),
                    needsReview, latest.getRetentionScheduleId(), latest.getApplicablePolicyIds());
        } catch (Exception e) {
            log.error("Sync classification failed for document {}: {}", event.documentId(), e.getMessage(), e);
            return co.uk.wolfnotsheep.llm.api.ClassifyResponse.error(e.getMessage());
        }
    }

    /**
     * Synchronous custom prompt execution for the REST API.
     * Sends a custom prompt to the LLM without MCP tools and returns the raw response.
     */
    public co.uk.wolfnotsheep.llm.api.ClassifyResponse executeCustomPrompt(String documentId, String extractedText,
                                                                       String systemPrompt, String userPromptTemplate) {
        try {
            String userPrompt = userPromptTemplate != null
                    ? userPromptTemplate.replace("{text}", extractedText != null ? extractedText : "")
                    : extractedText != null ? extractedText : "";

            // Call LLM WITHOUT MCP tools — just a simple prompt/response
            String response = llmClientFactory.callWithoutTools(
                    systemPrompt != null ? systemPrompt : "You are a helpful assistant.",
                    userPrompt,
                    java.util.Map.of());

            // Try to parse as JSON
            java.util.Map<String, Object> parsed = null;
            if (response != null) {
                try {
                    // Strip markdown code fences if present
                    String cleaned = response.trim();
                    if (cleaned.startsWith("```")) {
                        cleaned = cleaned.replaceAll("^```\\w*\\n?", "").replaceAll("\\n?```$", "").trim();
                    }
                    parsed = new com.fasterxml.jackson.databind.ObjectMapper().readValue(cleaned,
                            new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, Object>>() {});
                } catch (Exception ignored) { /* response isn't JSON — that's fine */ }
            }

            return co.uk.wolfnotsheep.llm.api.ClassifyResponse.customPrompt(response, parsed);
        } catch (Exception e) {
            log.error("Custom prompt failed for document {}: {}", documentId, e.getMessage(), e);
            return co.uk.wolfnotsheep.llm.api.ClassifyResponse.error(e.getMessage());
        }
    }

    private void classify(DocumentProcessedEvent event) {
        classifyCore(event);

        // Publish classified event for downstream consumers (async flow)
        List<DocumentClassificationResult> results =
                governanceService.getClassificationHistory(event.documentId());
        if (results.isEmpty()) return; // already handled in classifyCore

        DocumentClassificationResult latest = results.getFirst();
        double reviewThreshold = getReviewThreshold();
        double autoApproveThreshold = configService.getValue("pipeline.confidence.auto_approve_threshold", 0.95);
        boolean needsReview = latest.getConfidence() < reviewThreshold;
        boolean autoApproved = latest.getConfidence() >= autoApproveThreshold;

        if (autoApproved) {
            log.info("Document {} auto-approved (confidence {} >= threshold {})",
                    event.documentId(), latest.getConfidence(), autoApproveThreshold);
        }

        var classifiedEvent = new DocumentClassifiedEvent(
                event.documentId(),
                latest.getId(),
                latest.getCategoryId(),
                latest.getCategoryName(),
                latest.getSensitivityLabel(),
                latest.getTags(),
                latest.getApplicablePolicyIds(),
                latest.getRetentionScheduleId(),
                latest.getConfidence(),
                needsReview && !autoApproved,
                Instant.now()
        );

        try {
            rabbitTemplate.convertAndSend(
                    RabbitMqConfig.EXCHANGE,
                    RabbitMqConfig.ROUTING_CLASSIFIED,
                    classifiedEvent
            );
        } catch (Exception queueErr) {
            log.error("Failed to publish classified event for {}: {}", event.documentId(), queueErr.getMessage());
            documentService.setError(event.documentId(), DocumentStatus.CLASSIFICATION_FAILED,
                    "QUEUE", "Classified but failed to publish to enforcement queue: " + queueErr.getMessage());
            return;
        }

        log.info("Document {} classified as {} ({}) with confidence {}. Human review: {}",
                event.documentId(),
                latest.getCategoryName(),
                latest.getSensitivityLabel(),
                latest.getConfidence(),
                needsReview);
    }

    /**
     * Core classification logic shared by the RabbitMQ consumer and the REST API.
     * Calls the LLM with MCP tools, logs AI usage, and saves the classification result.
     * Does NOT publish to RabbitMQ — the caller decides what to do with the result.
     */
    void classifyCore(DocumentProcessedEvent event) {
        classifyCore(event, java.util.Map.of());
    }

    /**
     * Core classification logic with per-node LLM overrides.
     *
     * @param llmOverrides per-node config (provider, model, temperature, maxTokens) from visual editor.
     */
    void classifyCore(DocumentProcessedEvent event, java.util.Map<String, Object> llmOverrides) {
        DocumentModel doc = documentService.getById(event.documentId());
        String pipelineId = doc != null ? doc.getPipelineId() : null;

        if (pipelineId == null) {
            PipelineDefinition routed = pipelineRoutingService.resolve(null, event.mimeType());
            if (routed != null) pipelineId = routed.getId();
        }

        String systemPrompt = promptBuilder.buildSystemPrompt(pipelineId, llmOverrides);
        String userPrompt = promptBuilder.buildUserPrompt(event, pipelineId);

        log.info("Sending classification request for document: {} (pipeline: {})",
                event.documentId(), pipelineId != null ? pipelineId : "default");

        var usageLog = new co.uk.wolfnotsheep.document.models.AiUsageLog();
        usageLog.setUsageType("CLASSIFY");
        usageLog.setTriggeredBy(event.uploadedBy() != null ? event.uploadedBy() : "SYSTEM");
        usageLog.setDocumentId(event.documentId());
        usageLog.setDocumentName(event.fileName());
        usageLog.setProvider(llmClientFactory.getActiveProvider());
        usageLog.setModel(llmClientFactory.getActiveModel());
        usageLog.setPipelineId(pipelineId);
        usageLog.setSystemPrompt(systemPrompt.length() > 3000 ? systemPrompt.substring(0, 3000) + "..." : systemPrompt);
        usageLog.setUserPrompt(userPrompt.length() > 5000 ? userPrompt.substring(0, 5000) + "..." : userPrompt);

        long classifyStart = System.currentTimeMillis();
        long timeoutSec = resolveTimeout(llmOverrides);

        // Attempt 1
        String response = callWithTimeout(systemPrompt, userPrompt, llmOverrides, timeoutSec, event.documentId());

        usageLog.setDurationMs(System.currentTimeMillis() - classifyStart);
        usageLog.setResponse(response != null && response.length() > 5000 ? response.substring(0, 5000) + "..." : response);

        log.info("Classification response received for document: {}", event.documentId());

        List<DocumentClassificationResult> results =
                governanceService.getClassificationHistory(event.documentId());

        // Auto-retry once on NO_RESULT — the model often forgets the tool call on first pass
        // but succeeds when reminded explicitly.
        if (results.isEmpty()) {
            log.warn("Attempt 1 produced no tool call for document {} — retrying with stricter prompt", event.documentId());
            statusNotifier.emitLog(event.documentId(), event.fileName(), "CLASSIFICATION", "WARN",
                    "LLM did not call save_classification_result — retrying with stricter prompt", null);

            String retrySystemPrompt = systemPrompt + "\n\n" +
                    "================= RETRY — PREVIOUS ATTEMPT FAILED =================\n" +
                    "Your previous response did NOT call save_classification_result. That response\n" +
                    "has been discarded. You MUST end this turn by invoking save_classification_result.\n" +
                    "Do not repeat the analysis in prose. Pick the most appropriate category from the\n" +
                    "taxonomy you already retrieved and call the tool with confidence reflecting your\n" +
                    "certainty. If unsure, use a low confidence (0.3-0.5). The tool call is mandatory.";

            long retryStart = System.currentTimeMillis();
            String retryResponse = callWithTimeout(retrySystemPrompt, userPrompt, llmOverrides, timeoutSec, event.documentId());
            usageLog.setDurationMs(usageLog.getDurationMs() + (System.currentTimeMillis() - retryStart));
            usageLog.setResponse((response != null ? response : "") + "\n\n--- RETRY ---\n\n" +
                    (retryResponse != null && retryResponse.length() > 3000 ? retryResponse.substring(0, 3000) + "..." : retryResponse));

            results = governanceService.getClassificationHistory(event.documentId());
        }

        if (results.isEmpty()) {
            log.warn("Both attempts produced no classification result for document {}", event.documentId());
            usageLog.setStatus("NO_RESULT");
            usageLog.setErrorMessage("LLM did not call save_classification_result (attempted twice)");
            try { aiUsageLogRepo.save(usageLog); } catch (Exception e) { log.error("Failed to save AI usage log: {}", e.getMessage()); }
            documentService.setError(event.documentId(), DocumentStatus.CLASSIFICATION_FAILED,
                    "CLASSIFICATION", "LLM did not produce a classification result after 2 attempts");
            return;
        }

        DocumentClassificationResult latest = results.getFirst();

        if (latest.getModelId() == null || latest.getModelId().isBlank()) {
            latest.setModelId(usageLog.getModel());
            governanceService.saveClassificationResult(latest);
        }

        usageLog.setStatus("SUCCESS");
        usageLog.setReasoning(latest.getReasoning());
        usageLog.setResult(java.util.Map.of(
                "categoryName", latest.getCategoryName() != null ? latest.getCategoryName() : "",
                "sensitivityLabel", latest.getSensitivityLabel() != null ? latest.getSensitivityLabel().name() : "",
                "confidence", latest.getConfidence(),
                "tags", latest.getTags() != null ? latest.getTags() : java.util.List.of()
        ));
        try { aiUsageLogRepo.save(usageLog); } catch (Exception e) { log.error("Failed to save AI usage log: {}", e.getMessage()); }

        if (doc != null) {
            PipelineDefinition categoryPipeline = pipelineRoutingService.resolve(
                    latest.getCategoryId(), event.mimeType());
            if (categoryPipeline != null) {
                doc.setPipelineId(categoryPipeline.getId());
                documentService.save(doc);
            }
        }

        documentService.updateStatus(event.documentId(), DocumentStatus.CLASSIFIED, "SYSTEM");
    }

    // ── Async LLM job consumer ────────────────────────────────────────

    /**
     * Consumes async LLM job requests from the pipeline engine.
     * Runs classification or custom prompt, then publishes a completion event
     * so the pipeline engine can resume.
     */
    @RabbitListener(queues = RabbitMqConfig.QUEUE_LLM_JOBS)
    public void onLlmJobRequested(LlmJobRequestedEvent event) {
        log.info("Received async LLM job {} for document {} (mode: {}, node: {})",
                event.jobId(), event.documentId(), event.mode(), event.nodeKey());

        try {
            if ("CUSTOM_PROMPT".equals(event.mode())) {
                handleCustomPromptJob(event);
            } else {
                handleClassificationJob(event);
            }
        } catch (Exception e) {
            log.error("Async LLM job {} failed for document {}: {}",
                    event.jobId(), event.documentId(), e.getMessage(), e);
            publishJobCompleted(LlmJobCompletedEvent.failure(
                    event.jobId(), event.pipelineRunId(), event.nodeRunId(),
                    e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
        }
    }

    private void handleClassificationJob(LlmJobRequestedEvent event) {
        // Build a DocumentProcessedEvent to reuse existing classification logic
        var processedEvent = new DocumentProcessedEvent(
                event.documentId(), event.fileName(), event.mimeType(),
                event.fileSizeBytes(), event.extractedText(),
                "", // storageLocation not needed for classification
                event.uploadedBy(), java.time.Instant.now()
        );

        DocumentModel doc = documentService.getById(event.documentId());
        if (doc != null) {
            documentService.updateStatus(event.documentId(), DocumentStatus.CLASSIFYING, "SYSTEM");
        }

        statusNotifier.emitLog(event.documentId(), event.fileName(), "CLASSIFICATION", "INFO",
                "Async LLM classification starting — " + llmClientFactory.getActiveProvider().toUpperCase()
                + " (job: " + event.jobId() + ")", null);

        long start = System.currentTimeMillis();
        classifyCore(processedEvent);
        long durationMs = System.currentTimeMillis() - start;

        statusNotifier.emitLog(event.documentId(), event.fileName(), "CLASSIFICATION", "INFO",
                "Async LLM classification complete", durationMs);

        // Retrieve the classification result
        List<DocumentClassificationResult> results =
                governanceService.getClassificationHistory(event.documentId());
        if (results.isEmpty()) {
            publishJobCompleted(LlmJobCompletedEvent.failure(
                    event.jobId(), event.pipelineRunId(), event.nodeRunId(),
                    "LLM did not produce a classification result"));
            return;
        }

        DocumentClassificationResult latest = results.getFirst();
        double reviewThreshold = getReviewThreshold();
        boolean needsReview = latest.getConfidence() < reviewThreshold;

        publishJobCompleted(new LlmJobCompletedEvent(
                event.jobId(),
                event.pipelineRunId(),
                event.nodeRunId(),
                true,
                latest.getId(),
                latest.getCategoryId(),
                latest.getCategoryName(),
                latest.getSensitivityLabel() != null ? latest.getSensitivityLabel().name() : null,
                latest.getTags(),
                latest.getConfidence(),
                needsReview,
                latest.getRetentionScheduleId(),
                latest.getApplicablePolicyIds(),
                latest.getExtractedMetadata() != null ? new java.util.HashMap<>(latest.getExtractedMetadata()) : null,
                null, // customResult
                null, // error
                java.time.Instant.now()));

        log.info("Async LLM job {} completed — classified as {} (confidence: {}, review: {})",
                event.jobId(), latest.getCategoryName(), latest.getConfidence(), needsReview);
    }

    private void handleCustomPromptJob(LlmJobRequestedEvent event) {
        // Load block for custom prompt content
        String systemPrompt = "You are a helpful assistant.";
        String userPromptTemplate = "{text}";

        if (event.blockId() != null) {
            blockRepo.findById(event.blockId()).ifPresent(block -> {
                var content = block.getActiveContent();
                // custom prompt blocks won't affect the outer variables directly
            });
            var block = blockRepo.findById(event.blockId()).orElse(null);
            if (block != null && block.getActiveContent() != null) {
                var content = block.getActiveContent();
                if (content.get("systemPrompt") != null) systemPrompt = content.get("systemPrompt").toString();
                if (content.get("userPromptTemplate") != null) userPromptTemplate = content.get("userPromptTemplate").toString();
            }
        }

        var result = executeCustomPrompt(event.documentId(), event.extractedText(),
                systemPrompt, userPromptTemplate);

        if (result.success()) {
            publishJobCompleted(new LlmJobCompletedEvent(
                    event.jobId(), event.pipelineRunId(), event.nodeRunId(),
                    true, null, null, null, null, null, 0.0, false, null, null, null,
                    result.parsedResult(), null, java.time.Instant.now()));
        } else {
            publishJobCompleted(LlmJobCompletedEvent.failure(
                    event.jobId(), event.pipelineRunId(), event.nodeRunId(), result.error()));
        }
    }

    private void publishJobCompleted(LlmJobCompletedEvent event) {
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMqConfig.PIPELINE_EXCHANGE,
                    RabbitMqConfig.ROUTING_LLM_JOB_COMPLETED,
                    event);
        } catch (Exception e) {
            log.error("Failed to publish LlmJobCompletedEvent for job {}: {}",
                    event.jobId(), e.getMessage());
        }
    }

    /**
     * Resolve the LLM timeout in seconds. Resolution chain:
     *   1. Per-node override (`timeoutSeconds` on the visual node) — wins if > 0
     *   2. Per-provider config — `pipeline.llm.timeout_seconds.<provider>`
     *   3. Global fallback — `pipeline.llm.timeout_seconds`
     *   4. Hardcoded default (60s)
     * Provider is read from the active LLM client to keep the choice live.
     */
    private long resolveTimeout(java.util.Map<String, Object> overrides) {
        // Node-level override
        if (overrides != null && overrides.get("timeoutSeconds") != null) {
            try {
                long v = Long.parseLong(overrides.get("timeoutSeconds").toString().trim());
                if (v > 0) return v;
            } catch (NumberFormatException ignored) { /* fall through */ }
        }
        // Per-provider config
        String provider = llmClientFactory.getActiveProvider();
        if (provider != null && !provider.isBlank()) {
            Object perProvider = configService.getValue(
                    "pipeline.llm.timeout_seconds." + provider.toLowerCase(), null);
            if (perProvider != null) {
                try {
                    long v = Long.parseLong(perProvider.toString().trim());
                    if (v > 0) return v;
                } catch (NumberFormatException ignored) { /* fall through */ }
            }
        }
        // Legacy global config
        Object global = configService.getValue("pipeline.llm.timeout_seconds", 60);
        try {
            long v = Long.parseLong(global.toString().trim());
            return v > 0 ? v : 60;
        } catch (NumberFormatException ignored) { return 60; }
    }

    /**
     * Run the LLM call with a hard timeout. If the model wanders past the
     * configured timeout, abort the call and surface a timeout exception
     * rather than tying up the worker indefinitely.
     */
    private String callWithTimeout(String systemPrompt, String userPrompt,
                                   java.util.Map<String, Object> overrides,
                                   long timeoutSeconds, String docId) {
        java.util.concurrent.CompletableFuture<String> future =
                java.util.concurrent.CompletableFuture.supplyAsync(
                        () -> llmClientFactory.call(systemPrompt, userPrompt, overrides));
        try {
            return future.get(timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException te) {
            future.cancel(true);
            log.warn("LLM call timed out after {}s for document {}", timeoutSeconds, docId);
            throw new RuntimeException("LLM call timed out after " + timeoutSeconds + "s", te);
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof RuntimeException re) throw re;
            throw new RuntimeException(cause);
        }
    }

    /**
     * Get the review threshold from the ROUTER block if available,
     * falling back to AppConfigService.
     */
    private double getReviewThreshold() {
        try {
            List<PipelineBlock> routerBlocks = blockRepo.findByTypeAndActiveTrueOrderByNameAsc(
                    PipelineBlock.BlockType.ROUTER);
            if (!routerBlocks.isEmpty()) {
                java.util.Map<String, Object> content = routerBlocks.getFirst().getActiveContent();
                if (content != null && content.containsKey("threshold")) {
                    Object val = content.get("threshold");
                    if (val instanceof Number n) return n.doubleValue();
                    if (val instanceof String s) return Double.parseDouble(s);
                }
            }
        } catch (Exception e) {
            log.debug("Failed to load ROUTER block threshold, using config fallback: {}", e.getMessage());
        }
        return configService.getValue("pipeline.confidence.review_threshold", 0.7);
    }
}
