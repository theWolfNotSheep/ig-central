package co.uk.wolfnotsheep.llm.api;

/**
 * Request DTO for the synchronous classification endpoint.
 * Supports two modes: CLASSIFICATION (full MCP-based) and CUSTOM_PROMPT (arbitrary LLM instruction).
 */
public record ClassifyRequest(
        String documentId,
        String fileName,
        String mimeType,
        long fileSizeBytes,
        String extractedText,
        String storageLocation,
        String uploadedBy,
        String pipelineId,
        String blockId,            // which PROMPT block to use
        String mode                // "CLASSIFICATION" or "CUSTOM_PROMPT"
) {}
