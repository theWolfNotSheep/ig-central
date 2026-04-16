package co.uk.wolfnotsheep.document.events;

/**
 * Published by the pipeline engine when an async LLM node is reached.
 * Consumed by the LLM worker to perform classification or custom prompt execution.
 */
public record LlmJobRequestedEvent(
        String jobId,
        String pipelineRunId,
        String nodeRunId,
        String documentId,
        String nodeKey,
        String mode,            // CLASSIFICATION or CUSTOM_PROMPT
        String blockId,
        Integer blockVersion,
        String pipelineId,
        String extractedText,
        String fileName,
        String mimeType,
        long fileSizeBytes,
        String uploadedBy,
        String idempotencyKey
) {}
