package co.uk.wolfnotsheep.indexing.quarantine;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Mongo-mapped row in {@code index_quarantine}. Captures documents
 * whose ES write failed with a mapping conflict (or other 4xx)
 * — the worker parks them here for admin review rather than retrying
 * indefinitely. Per the Phase 1.11 plan + the contracted
 * `INDEX_MAPPING_CONFLICT` error code.
 */
@Document(collection = "index_quarantine")
public record QuarantineRecord(
        @Id String documentId,
        String reason,
        int httpStatus,
        String responseBody,
        String requestBody,
        Instant quarantinedAt
) {}
