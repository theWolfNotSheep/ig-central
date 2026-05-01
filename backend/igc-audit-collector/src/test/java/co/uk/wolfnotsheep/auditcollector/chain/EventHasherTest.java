package co.uk.wolfnotsheep.auditcollector.chain;

import co.uk.wolfnotsheep.auditcollector.store.StoredTier1Event;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EventHasherTest {

    @Test
    void hash_starts_with_sha256_prefix_and_is_64_hex_chars() {
        StoredTier1Event row = build("EV1", "DOCUMENT_CLASSIFIED",
                Instant.parse("2026-04-30T10:00:00Z"), "DOCUMENT", "doc-1", null);

        String hash = EventHasher.hashOf(row);

        assertThat(hash).startsWith("sha256:");
        assertThat(hash.substring(7)).hasSize(64).matches("[0-9a-f]{64}");
    }

    @Test
    void same_input_produces_same_hash() {
        StoredTier1Event a = build("EV1", "DOCUMENT_CLASSIFIED",
                Instant.parse("2026-04-30T10:00:00Z"), "DOCUMENT", "doc-1", null);
        StoredTier1Event b = build("EV1", "DOCUMENT_CLASSIFIED",
                Instant.parse("2026-04-30T10:00:00Z"), "DOCUMENT", "doc-1", null);

        assertThat(EventHasher.hashOf(a)).isEqualTo(EventHasher.hashOf(b));
    }

    @Test
    void different_eventId_produces_different_hash() {
        StoredTier1Event a = build("EV1", "DOCUMENT_CLASSIFIED",
                Instant.parse("2026-04-30T10:00:00Z"), "DOCUMENT", "doc-1", null);
        StoredTier1Event b = build("EV2", "DOCUMENT_CLASSIFIED",
                Instant.parse("2026-04-30T10:00:00Z"), "DOCUMENT", "doc-1", null);

        assertThat(EventHasher.hashOf(a)).isNotEqualTo(EventHasher.hashOf(b));
    }

    @Test
    void different_resource_produces_different_hash() {
        StoredTier1Event a = build("EV1", "DOCUMENT_CLASSIFIED",
                Instant.parse("2026-04-30T10:00:00Z"), "DOCUMENT", "doc-1", null);
        StoredTier1Event b = build("EV1", "DOCUMENT_CLASSIFIED",
                Instant.parse("2026-04-30T10:00:00Z"), "DOCUMENT", "doc-2", null);

        assertThat(EventHasher.hashOf(a)).isNotEqualTo(EventHasher.hashOf(b));
    }

    @Test
    void previous_hash_factors_into_chain() {
        StoredTier1Event a = build("EV1", "DOCUMENT_CLASSIFIED",
                Instant.parse("2026-04-30T10:00:00Z"), "DOCUMENT", "doc-1", null);
        StoredTier1Event b = build("EV1", "DOCUMENT_CLASSIFIED",
                Instant.parse("2026-04-30T10:00:00Z"), "DOCUMENT", "doc-1",
                "sha256:abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789");

        assertThat(EventHasher.hashOf(a)).isNotEqualTo(EventHasher.hashOf(b));
    }

    @Test
    void null_row_returns_null() {
        assertThat(EventHasher.hashOf(null)).isNull();
    }

    private static StoredTier1Event build(String eventId, String eventType, Instant ts,
                                          String resourceType, String resourceId,
                                          String previousEventHash) {
        return new StoredTier1Event(eventId, eventType, "1.0.0", ts,
                resourceId, null, null, null,
                "test-svc", "SYSTEM", resourceType, resourceId,
                "RECORD", "SUCCESS", "7Y", previousEventHash, Map.of());
    }
}
