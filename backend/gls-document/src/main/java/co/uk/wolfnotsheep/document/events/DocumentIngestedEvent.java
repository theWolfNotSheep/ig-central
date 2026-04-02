package co.uk.wolfnotsheep.document.events;

import java.time.Instant;

/**
 * Shared event record: published by the API on upload,
 * consumed by document-processing worker.
 */
public record DocumentIngestedEvent(
        String documentId,
        String fileName,
        String mimeType,
        long fileSizeBytes,
        String storageBucket,
        String storageKey,
        String uploadedBy,
        Instant ingestedAt
) {}
