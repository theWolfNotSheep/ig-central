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
        String mode,               // "CLASSIFICATION" or "CUSTOM_PROMPT"
        // Per-node LLM overrides (from pipeline visual editor)
        String provider,           // "anthropic" or "ollama" — null = use global default
        String model,              // model ID — null = use global default
        Double temperature,        // 0-1 — null = use global default
        Integer maxTokens,         // max response tokens — null = use global default
        Integer timeoutSeconds,    // hard timeout for this call — null = use per-provider default
        // Prompt-injection toggles (pre-load context, skip MCP round-trips)
        Boolean injectTaxonomy,
        Boolean injectSensitivities,
        Boolean injectTraits,
        Boolean injectPiiTypes
) {}
