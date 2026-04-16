package co.uk.wolfnotsheep.infrastructure.services.pipeline;

import co.uk.wolfnotsheep.document.events.LlmJobCompletedEvent;
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
 * Consumes LLM job completion events and resumes the paused pipeline.
 * This is the callback-driven counterpart to the async LLM dispatch in PipelineExecutionEngine.
 */
@Component
@ConditionalOnProperty(name = "pipeline.execution-engine.enabled", havingValue = "true")
public class PipelineResumeConsumer {

    private static final Logger log = LoggerFactory.getLogger(PipelineResumeConsumer.class);

    private final PipelineExecutionEngine engine;
    private final DocumentService documentService;
    private final SystemErrorRepository systemErrorRepo;

    public PipelineResumeConsumer(PipelineExecutionEngine engine,
                                  DocumentService documentService,
                                  SystemErrorRepository systemErrorRepo) {
        this.engine = engine;
        this.documentService = documentService;
        this.systemErrorRepo = systemErrorRepo;
    }

    @RabbitListener(queues = RabbitMqConfig.QUEUE_LLM_COMPLETED)
    public void onLlmJobCompleted(LlmJobCompletedEvent event) {
        log.info("[ResumeConsumer] Received LLM job completion: job={}, pipelineRun={}, success={}",
                event.jobId(), event.pipelineRunId(), event.success());

        try {
            engine.resumePipeline(event);
        } catch (Exception e) {
            log.error("[ResumeConsumer] Pipeline resume failed for job {}: {}",
                    event.jobId(), e.getMessage(), e);

            // Try to set error on the document
            try {
                // We don't have the documentId directly on the event easily,
                // but the engine's resumePipeline should have handled the error.
                // This is a last-resort fallback.
                var sysError = SystemError.of("CRITICAL", "PIPELINE_RESUME",
                        "Pipeline resume failed for job " + event.jobId()
                                + " (pipelineRun: " + event.pipelineRunId() + "): " + e.getMessage());
                sysError.setService("api");
                systemErrorRepo.save(sysError);
            } catch (Exception inner) {
                log.error("[ResumeConsumer] Failed to persist SystemError: {}", inner.getMessage());
            }
        }
    }
}
