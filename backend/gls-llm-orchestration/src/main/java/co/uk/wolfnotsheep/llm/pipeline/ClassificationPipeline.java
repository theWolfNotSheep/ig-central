package co.uk.wolfnotsheep.llm.pipeline;

import co.uk.wolfnotsheep.document.events.DocumentClassifiedEvent;
import co.uk.wolfnotsheep.document.events.DocumentProcessedEvent;
import co.uk.wolfnotsheep.document.models.DocumentModel;
import co.uk.wolfnotsheep.document.models.DocumentStatus;
import co.uk.wolfnotsheep.document.services.DocumentService;
import co.uk.wolfnotsheep.governance.models.DocumentClassificationResult;
import co.uk.wolfnotsheep.governance.models.PipelineBlock;
import co.uk.wolfnotsheep.governance.models.PipelineDefinition;
import co.uk.wolfnotsheep.governance.repositories.PipelineBlockRepository;
import co.uk.wolfnotsheep.governance.services.GovernanceService;
import co.uk.wolfnotsheep.governance.services.PipelineRoutingService;
import co.uk.wolfnotsheep.llm.config.RabbitMqConfig;
import co.uk.wolfnotsheep.llm.prompts.ClassificationPromptBuilder;
import co.uk.wolfnotsheep.platform.config.services.AppConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
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

    private final ChatClient chatClient;
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
    private final String llmProvider;

    public ClassificationPipeline(ChatClient chatClient,
                                  ClassificationPromptBuilder promptBuilder,
                                  GovernanceService governanceService,
                                  DocumentService documentService,
                                  PipelineRoutingService pipelineRoutingService,
                                  RabbitTemplate rabbitTemplate,
                                  AppConfigService configService,
                                  PipelineBlockRepository blockRepo,
                                  co.uk.wolfnotsheep.document.services.PipelineStatusNotifier statusNotifier,
                                  co.uk.wolfnotsheep.document.repositories.AiUsageLogRepository aiUsageLogRepo,
                                  co.uk.wolfnotsheep.document.repositories.SystemErrorRepository systemErrorRepo,
                                  @org.springframework.beans.factory.annotation.Value("${llm.provider:anthropic}") String llmProvider) {
        this.chatClient = chatClient;
        this.promptBuilder = promptBuilder;
        this.statusNotifier = statusNotifier;
        this.aiUsageLogRepo = aiUsageLogRepo;
        this.systemErrorRepo = systemErrorRepo;
        this.llmProvider = llmProvider;
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
                    "Sending to " + llmProvider.toUpperCase() + " for classification", null);
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
                String failProvider = configService.getValue("llm.provider", llmProvider);
                failLog.setProvider(failProvider);
                failLog.setModel(configService.getValue("llm." + failProvider + ".model", "unknown"));
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
        try {
            DocumentModel doc = documentService.getById(event.documentId());
            if (doc == null) {
                return co.uk.wolfnotsheep.llm.api.ClassifyResponse.error("Document not found: " + event.documentId());
            }

            documentService.updateStatus(event.documentId(), DocumentStatus.CLASSIFYING, "SYSTEM");
            statusNotifier.emitLog(event.documentId(), event.fileName(), "CLASSIFICATION", "INFO",
                    "Sending to " + llmProvider.toUpperCase() + " for classification (sync)", null);

            long start = System.currentTimeMillis();
            classifyCore(event);
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
            String response = chatClient.prompt()
                    .system(systemPrompt != null ? systemPrompt : "You are a helpful assistant.")
                    .user(userPrompt)
                    .call()
                    .content();

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
        boolean needsReview = latest.getConfidence() < reviewThreshold;

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
                needsReview,
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
        DocumentModel doc = documentService.getById(event.documentId());
        String pipelineId = doc != null ? doc.getPipelineId() : null;

        if (pipelineId == null) {
            PipelineDefinition routed = pipelineRoutingService.resolve(null, event.mimeType());
            if (routed != null) pipelineId = routed.getId();
        }

        String systemPrompt = promptBuilder.buildSystemPrompt(pipelineId);
        String userPrompt = promptBuilder.buildUserPrompt(event, pipelineId);

        log.info("Sending classification request for document: {} (pipeline: {})",
                event.documentId(), pipelineId != null ? pipelineId : "default");

        var usageLog = new co.uk.wolfnotsheep.document.models.AiUsageLog();
        usageLog.setUsageType("CLASSIFY");
        usageLog.setTriggeredBy(event.uploadedBy() != null ? event.uploadedBy() : "SYSTEM");
        usageLog.setDocumentId(event.documentId());
        usageLog.setDocumentName(event.fileName());
        String actualProvider = configService.getValue("llm.provider", llmProvider);
        usageLog.setProvider(actualProvider);
        usageLog.setModel(configService.getValue("llm." + actualProvider + ".model", "unknown"));
        usageLog.setPipelineId(pipelineId);
        usageLog.setSystemPrompt(systemPrompt.length() > 3000 ? systemPrompt.substring(0, 3000) + "..." : systemPrompt);
        usageLog.setUserPrompt(userPrompt.length() > 5000 ? userPrompt.substring(0, 5000) + "..." : userPrompt);

        long classifyStart = System.currentTimeMillis();

        String response = chatClient.prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .call()
                .content();

        usageLog.setDurationMs(System.currentTimeMillis() - classifyStart);
        usageLog.setResponse(response != null && response.length() > 5000 ? response.substring(0, 5000) + "..." : response);

        log.info("Classification response received for document: {}", event.documentId());

        List<DocumentClassificationResult> results =
                governanceService.getClassificationHistory(event.documentId());

        if (results.isEmpty()) {
            log.warn("No classification result saved by LLM for document {}. Response: {}",
                    event.documentId(), response);
            usageLog.setStatus("NO_RESULT");
            usageLog.setErrorMessage("LLM did not call save_classification_result");
            try { aiUsageLogRepo.save(usageLog); } catch (Exception e) { log.error("Failed to save AI usage log: {}", e.getMessage()); }
            documentService.setError(event.documentId(), DocumentStatus.CLASSIFICATION_FAILED,
                    "CLASSIFICATION", "LLM did not produce a classification result");
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
