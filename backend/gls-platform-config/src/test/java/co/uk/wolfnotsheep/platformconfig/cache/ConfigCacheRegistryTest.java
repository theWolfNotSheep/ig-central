package co.uk.wolfnotsheep.platformconfig.cache;

import co.uk.wolfnotsheep.platformconfig.event.ChangeType;
import co.uk.wolfnotsheep.platformconfig.event.ConfigChangedEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigCacheRegistryTest {

    @Test
    void targeted_event_invalidates_only_listed_ids() {
        ConfigCacheRegistry registry = new ConfigCacheRegistry();
        ConfigCache<String> cache = new ConfigCache<>("TAXONOMY");
        registry.register(cache);
        cache.get("a", k -> "A");
        cache.get("b", k -> "B");
        cache.get("c", k -> "C");

        registry.dispatch(ConfigChangedEvent.single("TAXONOMY", "b", ChangeType.UPDATED, "test"));

        assertThat(cache.size()).isEqualTo(2);
        // "b" is gone, "a" and "c" remain
        AtomicWasReloaded reloaded = new AtomicWasReloaded();
        cache.get("b", k -> { reloaded.flag(); return "B-reloaded"; });
        assertThat(reloaded.was).isTrue();
    }

    @Test
    void bulk_event_clears_entire_cache() {
        ConfigCacheRegistry registry = new ConfigCacheRegistry();
        ConfigCache<String> cache = new ConfigCache<>("POLICY");
        registry.register(cache);
        cache.get("p1", k -> "v1");
        cache.get("p2", k -> "v2");

        registry.dispatch(ConfigChangedEvent.bulk("POLICY", ChangeType.UPDATED, "test"));

        assertThat(cache.size()).isZero();
    }

    @Test
    void event_for_unregistered_type_is_silently_ignored() {
        ConfigCacheRegistry registry = new ConfigCacheRegistry();
        ConfigCache<String> taxonomyCache = new ConfigCache<>("TAXONOMY");
        registry.register(taxonomyCache);
        taxonomyCache.get("a", k -> "A");

        registry.dispatch(ConfigChangedEvent.bulk("UNKNOWN_TYPE", ChangeType.UPDATED, "test"));

        // TAXONOMY cache untouched
        assertThat(taxonomyCache.size()).isEqualTo(1);
    }

    @Test
    void multiple_caches_for_same_type_all_invalidate() {
        ConfigCacheRegistry registry = new ConfigCacheRegistry();
        ConfigCache<String> first = new ConfigCache<>("BLOCK");
        ConfigCache<String> second = new ConfigCache<>("BLOCK");
        registry.register(first);
        registry.register(second);
        first.get("x", k -> "1");
        second.get("x", k -> "2");

        registry.dispatch(ConfigChangedEvent.single("BLOCK", "x", ChangeType.DELETED, "test"));

        assertThat(first.size()).isZero();
        assertThat(second.size()).isZero();
        assertThat(registry.registrationCount("BLOCK")).isEqualTo(2);
    }

    private static final class AtomicWasReloaded {
        boolean was;
        void flag() { was = true; }
    }
}
