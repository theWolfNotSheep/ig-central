package co.uk.wolfnotsheep.mcp.config;

import co.uk.wolfnotsheep.platformconfig.event.ChangeType;
import co.uk.wolfnotsheep.platformconfig.event.ConfigChangedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class McpConfigInvalidatorTest {

    private CacheManager cacheManager;
    private Cache taxonomyCache;
    private Cache policiesCache;
    private McpConfigInvalidator invalidator;

    @BeforeEach
    void setUp() {
        cacheManager = mock(CacheManager.class);
        taxonomyCache = mock(Cache.class);
        policiesCache = mock(Cache.class);
        when(cacheManager.getCache(CacheConfig.CACHE_TAXONOMY)).thenReturn(taxonomyCache);
        when(cacheManager.getCache(CacheConfig.CACHE_POLICIES)).thenReturn(policiesCache);
        invalidator = new McpConfigInvalidator(cacheManager);
    }

    @Test
    void taxonomy_bulk_event_clears_taxonomy_cache_only() {
        invalidator.onConfigChanged(ConfigChangedEvent.bulk("TAXONOMY", ChangeType.UPDATED, "test"));

        verify(taxonomyCache).clear();
        verifyNoInteractions(policiesCache);
    }

    @Test
    void targeted_event_evicts_listed_ids() {
        invalidator.onConfigChanged(new ConfigChangedEvent(
                "POLICY", List.of("p1", "p2"), ChangeType.UPDATED,
                java.time.Instant.now(), "test", null));

        verify(policiesCache).evict(eq("p1"));
        verify(policiesCache).evict(eq("p2"));
        verify(policiesCache, never()).clear();
    }

    @Test
    void event_for_unmapped_entity_type_is_silently_ignored() {
        invalidator.onConfigChanged(ConfigChangedEvent.bulk(
                "NOT_A_CACHE_TYPE", ChangeType.UPDATED, "test"));

        verifyNoInteractions(taxonomyCache, policiesCache);
    }

    @Test
    void all_known_entity_types_have_cache_name_mappings() {
        // sanity that the mapping table covers every cache name declared
        // in CacheConfig — no orphans.
        java.util.Set<String> mappedCacheNames = new java.util.HashSet<>(
                CacheConfig.ENTITY_TYPE_TO_CACHE.values());
        java.util.Set<String> allCacheNames = java.util.Set.of(
                CacheConfig.CACHE_TAXONOMY, CacheConfig.CACHE_SENSITIVITIES,
                CacheConfig.CACHE_TRAITS, CacheConfig.CACHE_POLICIES,
                CacheConfig.CACHE_RETENTION, CacheConfig.CACHE_STORAGE,
                CacheConfig.CACHE_SCHEMAS, CacheConfig.CACHE_CORRECTIONS,
                CacheConfig.CACHE_PII_PATTERNS);
        org.assertj.core.api.Assertions.assertThat(mappedCacheNames)
                .containsExactlyInAnyOrderElementsOf(allCacheNames);
    }

    @Test
    void unregistered_cache_is_not_called_through() {
        when(cacheManager.getCache(CacheConfig.CACHE_TAXONOMY)).thenReturn(null);

        invalidator.onConfigChanged(ConfigChangedEvent.bulk("TAXONOMY", ChangeType.UPDATED, "test"));

        verify(taxonomyCache, times(0)).clear();
    }
}
