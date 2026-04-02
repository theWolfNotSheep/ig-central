package co.uk.wolfnotsheep.llm.prompts;

import co.uk.wolfnotsheep.document.events.DocumentProcessedEvent;
import org.springframework.stereotype.Component;

/**
 * Builds the system and user prompts for document classification.
 *
 * The system prompt instructs the LLM on its role and available tools.
 * The user prompt contains the document text to classify.
 *
 * The LLM is expected to:
 * 1. Call get_classification_taxonomy to understand available categories
 * 2. Call get_sensitivity_definitions to understand sensitivity levels
 * 3. Analyse the document text
 * 4. Call get_governance_policies to find applicable policies
 * 5. Call get_retention_schedules to determine retention
 * 6. Call get_storage_capabilities to recommend storage tier
 * 7. Call save_classification_result to persist its decision
 */
@Component
public class ClassificationPromptBuilder {

    public String buildSystemPrompt() {
        return """
                You are a document classification and information governance specialist.
                Your job is to analyse documents and classify them according to the organisation's
                governance framework.

                You have access to tools that let you query the organisation's:
                - Classification taxonomy (document categories)
                - Sensitivity label definitions
                - Governance policies (rules that apply to documents)
                - Retention schedules (how long documents must be kept)
                - Storage capabilities (where documents can be stored)

                For each document you receive, you MUST:

                1. First, retrieve the classification taxonomy and sensitivity definitions
                   to understand the organisation's framework.

                2. Read the document text carefully. Consider:
                   - What type of document is this? (contract, invoice, memo, report, etc.)
                   - Does it contain personally identifiable information (PII)?
                   - Does it contain financial, legal, or health-related information?
                   - Who is the intended audience?

                3. Classify the document into the most specific category in the taxonomy.
                   If no category fits well, use the closest parent category.

                4. Assign a sensitivity label. When uncertain, choose the HIGHER level.

                5. Extract relevant tags and structured metadata (dates, names, amounts, etc.).

                6. Retrieve applicable governance policies and the correct retention schedule.

                7. Save the classification result using the save_classification_result tool.
                   Include your confidence score (0.0-1.0) and detailed reasoning.

                Confidence guidelines:
                - 0.9-1.0: Clear, unambiguous classification
                - 0.7-0.89: High confidence but some ambiguity
                - 0.5-0.69: Moderate confidence — flag for human review
                - Below 0.5: Low confidence — definitely requires human review

                IMPORTANT: Always err on the side of higher sensitivity. A document
                classified as too sensitive is an inconvenience; a document classified
                as not sensitive enough is a compliance risk.
                """;
    }

    public String buildUserPrompt(DocumentProcessedEvent event) {
        return """
                Please classify the following document.

                **File name:** %s
                **MIME type:** %s
                **File size:** %d bytes
                **Uploaded by:** %s

                ---

                **Document text:**

                %s
                """.formatted(
                event.fileName(),
                event.mimeType(),
                event.fileSizeBytes(),
                event.uploadedBy(),
                truncateText(event.extractedText(), 100_000)
        );
    }

    private String truncateText(String text, int maxChars) {
        if (text == null) return "[No text extracted — binary or image-only document]";
        if (text.length() <= maxChars) return text;
        return text.substring(0, maxChars) + "\n\n[... truncated at " + maxChars + " characters]";
    }
}
