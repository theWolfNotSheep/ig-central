package co.uk.wolfnotsheep.auditcollector.consumer;

import co.uk.wolfnotsheep.auditcollector.chain.EventHasher;
import co.uk.wolfnotsheep.auditcollector.store.StoredTier1Event;
import co.uk.wolfnotsheep.auditcollector.store.Tier1Repository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class Tier1ConsumerTest {

    private Tier1Repository tier1Repo;
    private Tier1Consumer consumer;

    @BeforeEach
    void setUp() {
        tier1Repo = mock(Tier1Repository.class);
        consumer = new Tier1Consumer(tier1Repo);
    }

    @Test
    void first_in_chain_event_with_null_previousHash_is_persisted() {
        Map<String, Object> envelope = envelope("E1", "DOCUMENT_CLASSIFIED",
                "2026-04-30T10:00:00Z", "DOCUMENT", "doc-1", null);
        when(tier1Repo.findFirstByResourceTypeAndResourceIdOrderByTimestampDesc("DOCUMENT", "doc-1"))
                .thenReturn(Optional.empty());

        consumer.onTier1(envelope);

        verify(tier1Repo, times(1)).insert(any(StoredTier1Event.class));
    }

    @Test
    void event_with_correct_previousHash_chains_onto_existing_tail() {
        StoredTier1Event tail = build("E1", Instant.parse("2026-04-30T10:00:00Z"),
                "DOCUMENT", "doc-1", null);
        when(tier1Repo.findFirstByResourceTypeAndResourceIdOrderByTimestampDesc("DOCUMENT", "doc-1"))
                .thenReturn(Optional.of(tail));
        String correctHash = EventHasher.hashOf(tail);

        Map<String, Object> envelope = envelope("E2", "DOCUMENT_CLASSIFIED",
                "2026-04-30T10:01:00Z", "DOCUMENT", "doc-1", correctHash);

        consumer.onTier1(envelope);

        verify(tier1Repo, times(1)).insert(any(StoredTier1Event.class));
    }

    @Test
    void event_with_wrong_previousHash_is_dropped_not_persisted() {
        StoredTier1Event tail = build("E1", Instant.parse("2026-04-30T10:00:00Z"),
                "DOCUMENT", "doc-1", null);
        when(tier1Repo.findFirstByResourceTypeAndResourceIdOrderByTimestampDesc("DOCUMENT", "doc-1"))
                .thenReturn(Optional.of(tail));

        Map<String, Object> envelope = envelope("E2", "DOCUMENT_CLASSIFIED",
                "2026-04-30T10:01:00Z", "DOCUMENT", "doc-1",
                "sha256:0000000000000000000000000000000000000000000000000000000000000000");

        // No exception escapes — broken chain is acked + dropped per CLAUDE.md happy/unhappy.
        assertThatNoException().isThrownBy(() -> consumer.onTier1(envelope));
        verify(tier1Repo, never()).insert(any(StoredTier1Event.class));
    }

    @Test
    void duplicate_key_is_idempotent_no_op() {
        Map<String, Object> envelope = envelope("E1", "DOCUMENT_CLASSIFIED",
                "2026-04-30T10:00:00Z", "DOCUMENT", "doc-1", null);
        when(tier1Repo.findFirstByResourceTypeAndResourceIdOrderByTimestampDesc("DOCUMENT", "doc-1"))
                .thenReturn(Optional.empty());
        when(tier1Repo.insert(any(StoredTier1Event.class)))
                .thenThrow(new DuplicateKeyException("dup"));

        // No exception escapes — duplicate is treated as already-persisted.
        assertThatNoException().isThrownBy(() -> consumer.onTier1(envelope));
    }

    @Test
    void null_envelope_is_dropped_without_calling_repo() {
        consumer.onTier1(null);
        verify(tier1Repo, never()).insert(any(StoredTier1Event.class));
    }

    @Test
    void envelope_missing_resource_is_dropped() {
        Map<String, Object> envelope = new HashMap<>(Map.of(
                "eventId", "E1",
                "eventType", "DOCUMENT_CLASSIFIED",
                "schemaVersion", "1.0.0",
                "timestamp", "2026-04-30T10:00:00Z"
        ));
        consumer.onTier1(envelope);
        verify(tier1Repo, never()).insert(any(StoredTier1Event.class));
    }

    private static Map<String, Object> envelope(String eventId, String eventType, String ts,
                                                String resourceType, String resourceId,
                                                String previousEventHash) {
        Map<String, Object> e = new HashMap<>();
        e.put("eventId", eventId);
        e.put("eventType", eventType);
        e.put("tier", "DOMAIN");
        e.put("schemaVersion", "1.0.0");
        e.put("timestamp", ts);
        e.put("documentId", resourceId);
        e.put("actor", Map.of("type", "SYSTEM", "service", "test-svc"));
        e.put("resource", Map.of("type", resourceType, "id", resourceId));
        e.put("action", "RECORD");
        e.put("outcome", "SUCCESS");
        e.put("retentionClass", "7Y");
        if (previousEventHash != null) e.put("previousEventHash", previousEventHash);
        return e;
    }

    private static StoredTier1Event build(String eventId, Instant ts,
                                          String resourceType, String resourceId,
                                          String previousEventHash) {
        return new StoredTier1Event(eventId, "DOCUMENT_CLASSIFIED", "1.0.0", ts,
                resourceId, null, null, null,
                "test-svc", "SYSTEM", resourceType, resourceId,
                "RECORD", "SUCCESS", "7Y", previousEventHash, Map.of());
    }
}
