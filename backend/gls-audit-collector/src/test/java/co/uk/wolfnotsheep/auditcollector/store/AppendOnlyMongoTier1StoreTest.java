package co.uk.wolfnotsheep.auditcollector.store;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AppendOnlyMongoTier1StoreTest {

    private Tier1Repository repo;
    private AppendOnlyMongoTier1Store store;

    @BeforeEach
    void setUp() {
        repo = mock(Tier1Repository.class);
        store = new AppendOnlyMongoTier1Store(repo);
    }

    @Test
    void append_inserts_via_repo() {
        StoredTier1Event event = build("E1", "DOCUMENT", "doc-1");

        store.append(event);

        verify(repo, times(1)).insert(event);
    }

    @Test
    void append_translates_DuplicateKeyException_into_AppendOnlyViolationException() {
        StoredTier1Event event = build("E1", "DOCUMENT", "doc-1");
        when(repo.insert(event)).thenThrow(new DuplicateKeyException("dup"));

        assertThatThrownBy(() -> store.append(event))
                .isInstanceOf(AppendOnlyViolationException.class)
                .hasMessageContaining("E1")
                .hasMessageContaining("append-only");
    }

    @Test
    void findById_delegates_to_repo() {
        StoredTier1Event event = build("E1", "DOCUMENT", "doc-1");
        when(repo.findById("E1")).thenReturn(Optional.of(event));

        assertThat(store.findById("E1")).contains(event);
    }

    @Test
    void findLatestForResource_delegates_to_repo() {
        StoredTier1Event event = build("E1", "DOCUMENT", "doc-1");
        when(repo.findFirstByResourceTypeAndResourceIdOrderByTimestampDesc("DOCUMENT", "doc-1"))
                .thenReturn(Optional.of(event));

        assertThat(store.findLatestForResource("DOCUMENT", "doc-1")).contains(event);
    }

    @Test
    void findChainAsc_delegates_to_repo() {
        StoredTier1Event a = build("E1", "DOCUMENT", "doc-1");
        StoredTier1Event b = build("E2", "DOCUMENT", "doc-1");
        when(repo.findByResourceTypeAndResourceIdOrderByTimestampAsc("DOCUMENT", "doc-1"))
                .thenReturn(List.of(a, b));

        assertThat(store.findChainAsc("DOCUMENT", "doc-1")).containsExactly(a, b);
    }

    @Test
    void interface_does_not_expose_update_or_delete() {
        // Compile-time guarantee — Tier1Store has no save/update/delete methods.
        // This test exists to make the architectural intent explicit and to
        // catch a future PR that adds one (the test would have to change too).
        java.lang.reflect.Method[] methods = Tier1Store.class.getDeclaredMethods();
        for (java.lang.reflect.Method m : methods) {
            String name = m.getName();
            assertThat(name)
                    .withFailMessage("Tier1Store exposes mutating method: %s", name)
                    .isNotEqualTo("save")
                    .isNotEqualTo("update")
                    .isNotEqualTo("delete")
                    .isNotEqualTo("deleteById")
                    .isNotEqualTo("deleteAll");
        }
    }

    private static StoredTier1Event build(String eventId, String resourceType, String resourceId) {
        return new StoredTier1Event(eventId, "DOCUMENT_CLASSIFIED", "1.0.0",
                Instant.parse("2026-04-30T10:00:00Z"),
                resourceId, null, null, null,
                "test-svc", "SYSTEM", resourceType, resourceId,
                "RECORD", "SUCCESS", "7Y", null, Map.of());
    }
}
