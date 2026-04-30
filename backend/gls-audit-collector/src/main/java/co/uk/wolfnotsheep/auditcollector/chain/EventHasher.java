package co.uk.wolfnotsheep.auditcollector.chain;

import co.uk.wolfnotsheep.auditcollector.store.StoredTier1Event;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Deterministic hash of a Tier 1 audit event row, used as the
 * {@code previousEventHash} reference for the next event in the
 * resource's chain (CSV #4).
 *
 * <p>The hash input is a canonical string of the immutable identity
 * fields:
 * {@code eventId|eventType|timestamp.toString|resourceType:resourceId|previousEventHash}.
 * Per CLAUDE.md "Audit Relay Pattern", the publisher already strips
 * raw {@code details.content} values to {@code sha256:<hex>} hashes
 * before publishing — those hashes appear in the envelope map but do
 * not factor into the chain hash. The chain protects against
 * tampering of identity / ordering, not of content (which is itself
 * already hash-protected).
 */
public final class EventHasher {

    public static final String HASH_PREFIX = "sha256:";

    private EventHasher() { }

    public static String hashOf(StoredTier1Event row) {
        if (row == null) return null;
        StringBuilder sb = new StringBuilder(256);
        sb.append(safe(row.getEventId())).append('|');
        sb.append(safe(row.getEventType())).append('|');
        sb.append(row.getTimestamp() == null ? "" : row.getTimestamp().toString()).append('|');
        sb.append(safe(row.getResourceType())).append(':').append(safe(row.getResourceId())).append('|');
        sb.append(row.getPreviousEventHash() == null ? "" : row.getPreviousEventHash());
        return HASH_PREFIX + sha256Hex(sb.toString());
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available — no JVM ships without it", e);
        }
    }
}
