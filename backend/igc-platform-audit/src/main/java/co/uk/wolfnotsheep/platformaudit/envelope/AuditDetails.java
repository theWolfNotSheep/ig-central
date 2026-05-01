package co.uk.wolfnotsheep.platformaudit.envelope;

import java.util.Map;

/**
 * Event-type-specific payload for the audit envelope.
 *
 * <p>Per CSV #6, partitioned into {@code metadata} (always retained — survives
 * right-to-erasure) and {@code content} (subject to erasure; raw at Tier 2,
 * sha256-hashed at Tier 1 — the relay strips raw values when promoting).
 *
 * <p>{@code supersedes} / {@code supersededBy} (CSV #7) are ULID strings linking
 * a reclassification to the prior decision it replaces.
 *
 * @param metadata     Always-retained fields (compliance-essential).
 * @param content      Erasable fields (raw user content, model rationale, PII).
 * @param supersedes   Prior eventId this event replaces; nullable.
 * @param supersededBy Future eventId that replaces this one (set retroactively); nullable.
 */
public record AuditDetails(
        Map<String, Object> metadata,
        Map<String, Object> content,
        String supersedes,
        String supersededBy
) {

    public static AuditDetails metadataOnly(Map<String, Object> metadata) {
        return new AuditDetails(metadata, null, null, null);
    }

    public static AuditDetails of(Map<String, Object> metadata, Map<String, Object> content) {
        return new AuditDetails(metadata, content, null, null);
    }

    public AuditDetails withSupersedes(String priorEventId) {
        return new AuditDetails(metadata, content, priorEventId, supersededBy);
    }
}
