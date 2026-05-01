package co.uk.wolfnotsheep.auditcollector.store;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MongoTier2StoreTest {

    private Tier2Repository repo;
    private MongoTemplate mongoTemplate;
    private MongoTier2Store store;

    @BeforeEach
    void setUp() {
        repo = mock(Tier2Repository.class);
        mongoTemplate = mock(MongoTemplate.class);
        store = new MongoTier2Store(repo, mongoTemplate);
    }

    @Test
    void save_inserts_via_repo() {
        StoredTier2Event event = build("E1");

        store.save(event);

        verify(repo, times(1)).insert(event);
    }

    @Test
    void save_swallows_DuplicateKeyException_for_idempotency() {
        StoredTier2Event event = build("E1");
        when(repo.insert(event)).thenThrow(new DuplicateKeyException("dup"));

        assertThatNoException().isThrownBy(() -> store.save(event));
    }

    @Test
    void findById_delegates_to_repo() {
        StoredTier2Event event = build("E1");
        when(repo.findById("E1")).thenReturn(Optional.of(event));

        assertThat(store.findById("E1")).contains(event);
    }

    @Test
    void search_builds_query_with_active_filters_and_calls_template() {
        Tier2Store.SearchCriteria criteria = new Tier2Store.SearchCriteria(
                "doc-1", "DOCUMENT_CLASSIFIED", "test-svc",
                Instant.parse("2026-04-01T00:00:00Z"),
                Instant.parse("2026-05-01T00:00:00Z"));
        when(mongoTemplate.find(any(Query.class), eq(StoredTier2Event.class)))
                .thenReturn(List.of(build("E1")));

        List<StoredTier2Event> rows = store.search(criteria, 0, 50);

        assertThat(rows).hasSize(1);
        verify(mongoTemplate, times(1)).find(any(Query.class), eq(StoredTier2Event.class));
    }

    @Test
    void search_with_empty_criteria_still_calls_template() {
        when(mongoTemplate.find(any(Query.class), eq(StoredTier2Event.class)))
                .thenReturn(List.of());

        List<StoredTier2Event> rows = store.search(
                new Tier2Store.SearchCriteria(null, null, null, null, null), 0, 50);

        assertThat(rows).isEmpty();
    }

    private static StoredTier2Event build(String eventId) {
        return new StoredTier2Event(eventId, "DOCUMENT_CLASSIFIED", "1.0.0",
                Instant.parse("2026-04-30T10:00:00Z"),
                "doc-1", null, null, null,
                "test-svc", "SYSTEM", "DOCUMENT", "doc-1",
                "CLASSIFY", "SUCCESS", "30D", Map.of());
    }
}
