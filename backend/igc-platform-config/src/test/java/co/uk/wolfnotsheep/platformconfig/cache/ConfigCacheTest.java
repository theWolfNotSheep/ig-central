package co.uk.wolfnotsheep.platformconfig.cache;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfigCacheTest {

    @Test
    void miss_invokes_loader_and_caches_result() {
        ConfigCache<String> cache = new ConfigCache<>("TAXONOMY");
        AtomicInteger calls = new AtomicInteger();

        String first = cache.get("hr-leave", k -> { calls.incrementAndGet(); return "value-for-" + k; });
        String second = cache.get("hr-leave", k -> { calls.incrementAndGet(); return "should-not-be-called"; });

        assertThat(first).isEqualTo("value-for-hr-leave");
        assertThat(second).isEqualTo("value-for-hr-leave");
        assertThat(calls).hasValue(1);
    }

    @Test
    void invalidate_drops_single_entry() {
        ConfigCache<String> cache = new ConfigCache<>("TAXONOMY");
        cache.get("a", k -> "A");
        cache.get("b", k -> "B");

        cache.invalidate("a");

        assertThat(cache.size()).isEqualTo(1);
    }

    @Test
    void invalidate_all_clears_everything() {
        ConfigCache<String> cache = new ConfigCache<>("POLICY");
        cache.get("p1", k -> "v1");
        cache.get("p2", k -> "v2");
        cache.get("p3", k -> "v3");

        cache.invalidateAll();

        assertThat(cache.size()).isZero();
    }

    @Test
    void null_loader_result_is_not_cached() {
        ConfigCache<String> cache = new ConfigCache<>("X");
        AtomicInteger calls = new AtomicInteger();

        String first = cache.get("missing", k -> { calls.incrementAndGet(); return null; });
        String second = cache.get("missing", k -> { calls.incrementAndGet(); return null; });

        assertThat(first).isNull();
        assertThat(second).isNull();
        assertThat(calls).hasValue(2);
    }

    @Test
    void blank_entity_type_is_rejected() {
        assertThatThrownBy(() -> new ConfigCache<>("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ConfigCache<>(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
