package co.uk.wolfnotsheep.infrastructure.services.pipeline;

import co.uk.wolfnotsheep.document.events.DocumentClassifiedEvent;
import co.uk.wolfnotsheep.document.events.DocumentIngestedEvent;
import co.uk.wolfnotsheep.document.models.DocumentStatus;
import co.uk.wolfnotsheep.document.models.SystemError;
import co.uk.wolfnotsheep.document.repositories.SystemErrorRepository;
import co.uk.wolfnotsheep.document.services.DocumentService;
import co.uk.wolfnotsheep.infrastructure.config.RabbitMqConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * RabbitMQ consumer that delegates to the PipelineExecutionEngine.
 * Replaces the legacy DocumentProcessingPipeline and ClassificationEnforcementConsumer.
 *
 * Phase 1: Consumes document.ingested → runs pre-classification graph nodes
 * Phase 2: Consumes document.classified → runs post-classification graph nodes
 *
 * The LLM classification consumer (ClassificationPipeline in gls-llm-orchestration)
 * remains unchanged — it still consumes document.processed events from its own queue.
 */
@Component
@ConditionalOnProperty(name = "pipeline.execution-engine.enabled", havingValue = "true")
public class PipelineExecutionConsumer {

    private static final Logger log = LoggerFactory.getLogger(PipelineExecutionConsumer.class);

    private final PipelineExecutionEngine engine;
    private final DocumentService documentService;
    private final SystemErrorRepository systemErrorRepo;

    public PipelineExecutionConsumer(PipelineExecutionEngine engine,
                                     DocumentService documentService,
                                     SystemErrorRepository systemErrorRepo) {
        this.engine = engine;
        this.documentService = documentService;
        this.systemErrorRepo = systemErrorRepo;
    }

    @RabbitListener(queues = RabbitMqConfig.QUEUE_INGESTED)
    public void onDocumentIngested(DocumentIngestedEvent event) {
        log.info("[EngineConsumer] Received ingested event for document: {} ({})",
                event.documentId(), event.fileName());
        try {
            // Unified single-pass execution — walks entire graph including LLM nodes
            engine.executePipeline(event);
        } catch (Exception e) {
            log.error("[EngineConsumer] Pipeline failed for document {}: {}", event.documentId(), e.getMessage(), e);
            safeSetError(event.documentId(), DocumentStatus.PROCESSING_FAILED, "ENGINE", e.getMessage());
        }
    }

    @RabbitListener(queues = RabbitMqConfig.QUEUE_CLASSIFIED)
    public void onDocumentClassified(DocumentClassifiedEvent event) {
        // This listener is kept for backward compatibility (ASYNC_BOUNDARY fallback).
        // In unified mode, the pipeline already completed — skip if doc is past CLASSIFIED.
        try {
            var doc = documentService.getById(event.documentId());
            if (doc == null) return;
            var status = doc.getStatus();
            if (status == DocumentStatus.INBOX || status == DocumentStatus.GOVERNANCE_APPLIED
                    || status == DocumentStatus.FILED || status == DocumentStatus.REVIEW_REQUIRED) {
                log.info("[EngineConsumer] Document {} already at {} — unified pipeline completed, skipping Phase 2",
                        event.documentId(), status);
                return;
            }
            log.info("[EngineConsumer] Received classified event for document: {} — running Phase 2 (fallback)",
                    event.documentId());
            engine.executePhase2(event);
        } catch (Exception e) {
            log.error("[EngineConsumer] Phase 2 failed for document {}: {}", event.documentId(), e.getMessage(), e);
            safeSetError(event.documentId(), DocumentStatus.ENFORCEMENT_FAILED, "ENGINE_PHASE2", e.getMessage());
        }
    }

    private void safeSetError(String documentId, DocumentStatus failedStatus, String stage, String message) {
        try {
            documentService.setError(documentId, failedStatus, stage, message);
        } catch (Exception inner) {
            log.error("[EngineConsumer] Failed to set error status for {}: {}", documentId, inner.getMessage());
            try {
                SystemError error = SystemError.of("CRITICAL", "PIPELINE",
                        "Pipeline consumer failed AND could not set error status for document " + documentId + ": " + message);
                error.setDocumentId(documentId);
                error.setService("api");
                systemErrorRepo.save(error);
            } catch (Exception ex) {
                log.error("[EngineConsumer] Failed to persist SystemError for {}: {}", documentId, ex.getMessage());
            }
        }
    }
}
