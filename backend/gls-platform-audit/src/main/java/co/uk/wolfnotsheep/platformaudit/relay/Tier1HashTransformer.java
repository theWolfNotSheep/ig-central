package co.uk.wolfnotsheep.platformaudit.relay;

import co.uk.wolfnotsheep.platformaudit.envelope.AuditDetails;
import co.uk.wolfnotsheep.platformaudit.envelope.AuditEvent;
import co.uk.wolfnotsheep.platformaudit.envelope.Tier;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Per CSV #6: Tier 1 envelopes never carry raw {@code details.content}
 * values — the relay strips them to {@code sha256:<hex>} hashes when
 * promoting an event from the outbox to the Tier 1 channel. Tier 2
 * envelopes pass through unchanged.
 *
 * <p>The transformation is a no-op when:
 *
 * <ul>
 *     <li>{@code envelope.tier()} is not {@link Tier#DOMAIN}, or</li>
 *     <li>the envelope has no {@code details}, or</li>
 *     <li>{@code details.content()} is null or empty.</li>
 * </ul>
 *
 * <p>{@code details.metadata()}, {@code supersedes}, and
 * {@code supersededBy} are preserved verbatim — only {@code content} is
 * hashed. The right-to-erasure boundary lives at the metadata/content
 * partition; metadata survives erasure, content does not.
 */
public final class Tier1HashTransformer {

    private static final String HASH_PREFIX = "sha256:";

    private Tier1HashTransformer() {
    }

    public static AuditEvent toTier1(AuditEvent envelope) {
        if (envelope.tier() != Tier.DOMAIN) {
            return envelope;
        }
        AuditDetails original = envelope.details();
        if (original == null || original.content() == null || original.content().isEmpty()) {
            return envelope;
        }
        Map<String, Object> hashed = new LinkedHashMap<>(original.content().size());
        original.content().forEach((key, value) -> hashed.put(key, hash(value)));
        AuditDetails sanitised = new AuditDetails(
                original.metadata(),
                hashed,
                original.supersedes(),
                original.supersededBy());
        return new AuditEvent(
                envelope.eventId(),
                envelope.eventType(),
                envelope.tier(),
                envelope.schemaVersion(),
                envelope.timestamp(),
                envelope.documentId(),
                envelope.pipelineRunId(),
                envelope.nodeRunId(),
                envelope.traceparent(),
                envelope.actor(),
                envelope.resource(),
                envelope.action(),
                envelope.outcome(),
                sanitised,
                envelope.retentionClass(),
                envelope.previousEventHash());
    }

    private static String hash(Object value) {
        if (value == null) {
            return HASH_PREFIX + sha256Hex("");
        }
        return HASH_PREFIX + sha256Hex(value.toString());
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available — no JVM ships without it", e);
        }
    }
}
