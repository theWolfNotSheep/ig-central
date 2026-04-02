package co.uk.wolfnotsheep.llm.pipeline;

import co.uk.wolfnotsheep.document.events.DocumentClassifiedEvent;
import co.uk.wolfnotsheep.document.events.DocumentProcessedEvent;
import co.uk.wolfnotsheep.governance.models.DocumentClassificationResult;
import co.uk.wolfnotsheep.governance.services.GovernanceService;
import co.uk.wolfnotsheep.llm.config.RabbitMqConfig;
import co.uk.wolfnotsheep.llm.prompts.ClassificationPromptBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallbackProvider;
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
    private static final double HUMAN_REVIEW_THRESHOLD = 0.7;

    private final ChatClient chatClient;
    private final ClassificationPromptBuilder promptBuilder;
    private final GovernanceService governanceService;
    private final RabbitTemplate rabbitTemplate;

    public ClassificationPipeline(ChatClient.Builder chatClientBuilder,
                                  ToolCallbackProvider[] toolCallbackProviders,
                                  ClassificationPromptBuilder promptBuilder,
                                  GovernanceService governanceService,
                                  RabbitTemplate rabbitTemplate) {
        // Build ChatClient with all MCP tools available
        this.chatClient = chatClientBuilder
                .defaultToolCallbacks(toolCallbackProviders)
                .build();
        this.promptBuilder = promptBuilder;
        this.governanceService = governanceService;
        this.rabbitTemplate = rabbitTemplate;
    }

    @RabbitListener(queues = RabbitMqConfig.QUEUE_PROCESSED)
    public void onDocumentProcessed(DocumentProcessedEvent event) {
        log.info("Received document for classification: {} ({})", event.documentId(), event.fileName());

        try {
            classify(event);
        } catch (Exception e) {
            log.error("Classification failed for document {}: {}", event.documentId(), e.getMessage(), e);
            throw new RuntimeException("Classification failed", e);
        }
    }

    private void classify(DocumentProcessedEvent event) {
        String systemPrompt = promptBuilder.buildSystemPrompt();
        String userPrompt = promptBuilder.buildUserPrompt(event);

        log.info("Sending classification request to Claude for document: {}", event.documentId());

        // Call Claude with MCP tools — the model will invoke tools like
        // get_classification_taxonomy, get_sensitivity_definitions, etc.
        // and finally call save_classification_result to persist the decision.
        String response = chatClient.prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .call()
                .content();

        log.info("Claude classification response received for document: {}", event.documentId());

        // After the LLM call, save_classification_result tool should have been invoked.
        // Retrieve the latest classification result for this document.
        List<DocumentClassificationResult> results =
                governanceService.getClassificationHistory(event.documentId());

        if (results.isEmpty()) {
            log.warn("No classification result saved by LLM for document {}. Response: {}",
                    event.documentId(), response);
            return;
        }

        DocumentClassificationResult latest = results.getFirst();
        boolean needsReview = latest.getConfidence() < HUMAN_REVIEW_THRESHOLD;

        // Publish classified event for downstream consumers
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

        rabbitTemplate.convertAndSend(
                RabbitMqConfig.EXCHANGE,
                RabbitMqConfig.ROUTING_CLASSIFIED,
                classifiedEvent
        );

        log.info("Document {} classified as {} ({}) with confidence {}. Human review: {}",
                event.documentId(),
                latest.getCategoryName(),
                latest.getSensitivityLabel(),
                latest.getConfidence(),
                needsReview);
    }
}
