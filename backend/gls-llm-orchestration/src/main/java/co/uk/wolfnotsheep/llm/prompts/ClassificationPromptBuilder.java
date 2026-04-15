package co.uk.wolfnotsheep.llm.prompts;

import co.uk.wolfnotsheep.document.events.DocumentProcessedEvent;
import co.uk.wolfnotsheep.governance.models.PipelineBlock;
import co.uk.wolfnotsheep.governance.models.PipelineDefinition;
import co.uk.wolfnotsheep.governance.models.PipelineDefinition.PipelineStep;
import co.uk.wolfnotsheep.governance.repositories.PipelineBlockRepository;
import co.uk.wolfnotsheep.governance.repositories.PipelineDefinitionRepository;
import co.uk.wolfnotsheep.platform.config.services.AppConfigService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class ClassificationPromptBuilder {

    private static final String DEFAULT_SYSTEM_PROMPT = """
            You are a document classification and information governance specialist.
            Your job is to analyse documents and classify them according to the organisation's
            governance framework.

            You MUST follow this workflow in order:
            1. Call get_classification_taxonomy to retrieve the category hierarchy
            2. Call get_sensitivity_definitions to understand sensitivity levels
            3. Call get_correction_history with the likely category and document MIME type — this returns
               past human corrections. Use these to avoid repeating known mistakes.
            4. Call get_org_pii_patterns to check for organisation-specific PII types and known false positives
            5. Call get_governance_policies if relevant policies might affect classification
            6. Call get_document_traits to understand what document characteristics to detect
            7. Analyse the document text against the taxonomy, corrections, PII patterns, and traits
            8. Call get_metadata_schemas with the category ID you've chosen — if a schema exists,
               extract the defined fields from the document text
            9. Call save_classification_result with your decision. Include:
               - extractedMetadata as JSON if a schema applied
               - traits as comma-separated list (e.g. "FINAL,INBOUND,ORIGINAL")
               - piiFindings as a JSON array of any NEW PII you identified beyond what the
                 regex scanner already detected (shown in the prompt). Include contextual PII
                 like person names, addresses, identifiers that regex cannot catch. Format:
                 [{"type":"PERSON_NAME","redactedText":"Joh***ith","confidence":0.9}]

            When corrections exist for a category, weight them heavily — a human records manager
            has explicitly flagged these as errors. If corrections indicate a category or sensitivity
            was consistently wrong, adjust your decision accordingly.

            METADATA EXTRACTION RULES:
            - Only extract fields defined in the metadata schema for the matched category
            - If no schema exists for the category, do NOT include extractedMetadata at all
            - Never invent fields that aren't in the schema
            - For required fields you cannot find in the document, set the value to "NOT_FOUND"
            - Be precise: dates in ISO format (YYYY-MM-DD), amounts with currency symbol, full names
            - Use the extraction hints provided on each field to know WHERE to look in the document
            """;

    private static final String DEFAULT_USER_TEMPLATE = """
            Please classify the following document.

            **Document ID:** {documentId}
            **File name:** {fileName}
            **MIME type:** {mimeType}
            **File size:** {fileSizeBytes} bytes
            **Uploaded by:** {uploadedBy}

            IMPORTANT: When calling save_classification_result, you MUST use the exact
            Document ID shown above ({documentId}) as the documentId parameter.

            {piiSection}

            ---

            **Document text:**

            {extractedText}
            """;

    private final PipelineDefinitionRepository pipelineRepo;
    private final PipelineBlockRepository blockRepo;
    private final AppConfigService configService;

    public ClassificationPromptBuilder(PipelineDefinitionRepository pipelineRepo,
                                        PipelineBlockRepository blockRepo,
                                        AppConfigService configService) {
        this.pipelineRepo = pipelineRepo;
        this.blockRepo = blockRepo;
        this.configService = configService;
    }

    public String buildSystemPrompt() {
        return buildSystemPrompt(null);
    }

    public String buildSystemPrompt(String pipelineId) {
        String prompt = getStepPrompt(pipelineId, "LLM_CLASSIFICATION", true);
        return prompt != null ? prompt : DEFAULT_SYSTEM_PROMPT;
    }

    public String buildUserPrompt(DocumentProcessedEvent event) {
        return buildUserPrompt(event, null);
    }

    public String buildUserPrompt(DocumentProcessedEvent event, String pipelineId) {
        String template = getStepPrompt(pipelineId, "LLM_CLASSIFICATION", false);
        if (template == null) template = DEFAULT_USER_TEMPLATE;

        int maxLength = configService.getValue("pipeline.text.max_length", 100000);

        // Build PII section from Tier 1 regex findings
        String piiSection = "";
        if (event.piiFindings() != null && !event.piiFindings().isEmpty()) {
            StringBuilder sb = new StringBuilder();
            sb.append("**Pre-detected PII (Tier 1 regex scan):**\n");
            sb.append("The following PII entities were detected by pattern matching. ");
            sb.append("Validate these findings and report any additional PII you identify ");
            sb.append("when calling save_classification_result (use the piiFindings parameter).\n\n");
            for (var pii : event.piiFindings()) {
                sb.append("- ").append(pii.type()).append(": ").append(pii.redactedText())
                        .append(" (confidence: ").append(String.format("%.0f%%", pii.confidence() * 100))
                        .append(", method: ").append(pii.method()).append(")\n");
            }
            piiSection = sb.toString();
        }

        return template
                .replace("{documentId}", event.documentId())
                .replace("{fileName}", event.fileName())
                .replace("{mimeType}", event.mimeType())
                .replace("{fileSizeBytes}", String.valueOf(event.fileSizeBytes()))
                .replace("{uploadedBy}", event.uploadedBy())
                .replace("{piiSection}", piiSection)
                .replace("{extractedText}", truncateText(event.extractedText(), maxLength));
    }

    /**
     * Find the prompt from a specific pipeline (or the first active one).
     * @param pipelineId the pipeline to load (null = first active)
     * @param handler the config handler key (e.g. "LLM_CLASSIFICATION")
     * @param system true for systemPrompt, false for userPromptTemplate
     */
    private String getStepPrompt(String pipelineId, String handler, boolean system) {
        PipelineDefinition pipeline;
        if (pipelineId != null) {
            pipeline = pipelineRepo.findById(pipelineId).orElse(null);
        } else {
            List<PipelineDefinition> active = pipelineRepo.findByActiveTrue();
            pipeline = active.isEmpty() ? null : active.getFirst();
        }
        if (pipeline == null || pipeline.getSteps() == null) return null;

        for (PipelineStep step : pipeline.getSteps()) {
            if (!step.isEnabled()) continue;
            if (step.getConfig() != null && handler.equals(step.getConfig().get("handler"))) {
                // Check block reference first
                if (step.getBlockId() != null) {
                    String fromBlock = getPromptFromBlock(step.getBlockId(), step.getBlockVersion(), system);
                    if (fromBlock != null) return fromBlock;
                }
                // Fallback to inline prompts (legacy)
                String prompt = system ? step.getSystemPrompt() : step.getUserPromptTemplate();
                if (prompt != null && !prompt.isBlank()) return prompt;
            }
        }
        return null;
    }

    /**
     * Load prompt content from a PipelineBlock.
     */
    private String getPromptFromBlock(String blockId, Integer version, boolean system) {
        return blockRepo.findById(blockId).map(block -> {
            int v = version != null ? version : block.getActiveVersion();
            Map<String, Object> content = block.getVersionContent(v);
            if (content == null) return null;
            String key = system ? "systemPrompt" : "userPromptTemplate";
            Object val = content.get(key);
            return val != null ? val.toString() : null;
        }).orElse(null);
    }

    private String truncateText(String text, int maxChars) {
        if (text == null) return "[No text extracted — binary or image-only document]";
        if (text.length() <= maxChars) return text;
        return text.substring(0, maxChars) + "\n\n[... truncated at " + maxChars + " characters]";
    }
}
