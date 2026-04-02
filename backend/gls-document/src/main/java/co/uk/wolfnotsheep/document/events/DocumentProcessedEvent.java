package co.uk.wolfnotsheep.document.events;

import java.time.Instant;

/**
 * Shared event record: published by document-processing worker,
 * consumed by llm-orchestration worker.
 */
public record DocumentProcessedEvent(
        String documentId,
        String fileName,
        String mimeType,
        long fileSizeBytes,
        String extractedText,
        String storageLocation,
        String uploadedBy,
        Instant processedAt
) {}
