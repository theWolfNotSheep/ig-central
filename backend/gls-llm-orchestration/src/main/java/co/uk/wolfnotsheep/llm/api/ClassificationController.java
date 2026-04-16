package co.uk.wolfnotsheep.llm.api;

import co.uk.wolfnotsheep.document.events.DocumentProcessedEvent;
import co.uk.wolfnotsheep.governance.models.PipelineBlock;
import co.uk.wolfnotsheep.governance.repositories.PipelineBlockRepository;
import co.uk.wolfnotsheep.llm.pipeline.ClassificationPipeline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

/**
 * REST endpoint for synchronous LLM classification.
 * Called by the PipelineExecutionEngine (via SyncLlmNodeExecutor) when
 * it encounters an aiClassification node during single-pass execution.
 *
 * Two modes:
 * - CLASSIFICATION: full MCP-based classification (same as the RabbitMQ flow)
 * - CUSTOM_PROMPT: arbitrary LLM instruction using a linked PROMPT block
 */
@RestController
@RequestMapping("/api/internal")
public class ClassificationController {

    private static final Logger log = LoggerFactory.getLogger(ClassificationController.class);

    private final ClassificationPipeline classificationPipeline;
    private final PipelineBlockRepository blockRepo;

    public ClassificationController(ClassificationPipeline classificationPipeline,
                                     PipelineBlockRepository blockRepo) {
        this.classificationPipeline = classificationPipeline;
        this.blockRepo = blockRepo;
    }

    @PostMapping("/classify")
    public ResponseEntity<ClassifyResponse> classify(@RequestBody ClassifyRequest request) {
        log.info("[SyncClassify] Received request for document: {} mode: {}", request.documentId(), request.mode());

        if (request.documentId() == null || request.extractedText() == null) {
            return ResponseEntity.badRequest().body(ClassifyResponse.error("documentId and extractedText are required"));
        }

        String mode = request.mode() != null ? request.mode() : "CLASSIFICATION";

        if ("CUSTOM_PROMPT".equals(mode)) {
            return handleCustomPrompt(request);
        }

        return handleClassification(request);
    }

    private ResponseEntity<ClassifyResponse> handleClassification(ClassifyRequest request) {
        var event = new DocumentProcessedEvent(
                request.documentId(),
                request.fileName(),
                request.mimeType(),
                request.fileSizeBytes(),
                request.extractedText(),
                request.storageLocation(),
                request.uploadedBy(),
                Instant.now()
        );

        // Build per-node LLM overrides from request
        var overrides = new java.util.HashMap<String, Object>();
        if (request.provider() != null) overrides.put("provider", request.provider());
        if (request.model() != null) overrides.put("model", request.model());
        if (request.temperature() != null) overrides.put("temperature", request.temperature());
        if (request.maxTokens() != null) overrides.put("maxTokens", request.maxTokens());
        if (request.timeoutSeconds() != null) overrides.put("timeoutSeconds", request.timeoutSeconds());
        if (request.injectTaxonomy() != null) overrides.put("injectTaxonomy", request.injectTaxonomy());
        if (request.injectSensitivities() != null) overrides.put("injectSensitivities", request.injectSensitivities());
        if (request.injectTraits() != null) overrides.put("injectTraits", request.injectTraits());
        if (request.injectPiiTypes() != null) overrides.put("injectPiiTypes", request.injectPiiTypes());

        ClassifyResponse response = classificationPipeline.classifyInternal(event, overrides);
        return ResponseEntity.ok(response);
    }

    private ResponseEntity<ClassifyResponse> handleCustomPrompt(ClassifyRequest request) {
        // Load prompt from the linked block
        String systemPrompt = null;
        String userPromptTemplate = null;

        if (request.blockId() != null) {
            PipelineBlock block = blockRepo.findById(request.blockId()).orElse(null);
            if (block != null) {
                Map<String, Object> content = block.getActiveContent();
                if (content != null) {
                    Object sp = content.get("systemPrompt");
                    if (sp != null) systemPrompt = sp.toString();
                    Object up = content.get("userPromptTemplate");
                    if (up != null) userPromptTemplate = up.toString();
                }
            }
        }

        // If no block or block has no prompts, check if there's inline config
        if (systemPrompt == null && userPromptTemplate == null) {
            return ResponseEntity.badRequest().body(
                    ClassifyResponse.error("CUSTOM_PROMPT mode requires a linked PROMPT block with systemPrompt or userPromptTemplate"));
        }

        // Substitute {text} placeholder in user prompt with extracted text
        if (userPromptTemplate != null) {
            userPromptTemplate = userPromptTemplate.replace("{text}", request.extractedText() != null ? request.extractedText() : "");
        } else {
            userPromptTemplate = request.extractedText();
        }

        ClassifyResponse response = classificationPipeline.executeCustomPrompt(
                request.documentId(), request.extractedText(), systemPrompt, userPromptTemplate);
        return ResponseEntity.ok(response);
    }
}
