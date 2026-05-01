package co.uk.wolfnotsheep.document.events;

import java.time.Instant;
import java.util.List;

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
        Instant processedAt,
        List<PiiSummaryEntry> piiFindings
) {
    /** Backward-compatible constructor (no PII findings). */
    public DocumentProcessedEvent(String documentId, String fileName, String mimeType,
                                   long fileSizeBytes, String extractedText,
                                   String storageLocation, String uploadedBy, Instant processedAt) {
        this(documentId, fileName, mimeType, fileSizeBytes, extractedText,
                storageLocation, uploadedBy, processedAt, List.of());
    }

    /** Lightweight PII summary for the LLM — no raw matched text, just type + redacted + confidence. */
    public record PiiSummaryEntry(String type, String redactedText, double confidence, String method) {}
}
