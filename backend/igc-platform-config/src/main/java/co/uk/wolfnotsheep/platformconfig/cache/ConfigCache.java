package co.uk.wolfnotsheep.platformconfig.cache;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Per-replica in-memory cache for a single governance entity type.
 * Backed by {@link ConcurrentHashMap} — thread-safe, no TTL, no
 * concurrent re-loads of the same key (via
 * {@link ConcurrentHashMap#computeIfAbsent}).
 *
 * <p>Keys are strings — the caller's responsibility to convert from
 * domain-specific id types if needed. This keeps the change-driven
 * invalidation contract simple: the wire payload's {@code entityIds} are
 * strings, and {@link ConfigCacheRegistry} dispatches them straight
 * through to {@link #invalidate(String)} without per-cache type
 * conversion.
 *
 * <p>The cache only knows how to invalidate; it does not know how to
 * load. Callers pass a loader to {@link #get(String, Function)} on each
 * call site. This matches the existing {@code AppConfigService} pattern
 * — the cache is dumb on purpose.
 */
public class ConfigCache<V> {

    private final String entityType;
    private final Map<String, V> entries = new ConcurrentHashMap<>();

    public ConfigCache(String entityType) {
        if (entityType == null || entityType.isBlank()) {
            throw new IllegalArgumentException("entityType is required");
        }
        this.entityType = entityType;
    }

    public String entityType() {
        return entityType;
    }

    /**
     * Returns the cached value for {@code key}, computing it via the
     * loader on cache miss. Atomically: {@link ConcurrentHashMap#computeIfAbsent}
     * guarantees only one concurrent caller invokes the loader for a
     * given key.
     *
     * <p>If the loader returns {@code null} the cache stores nothing —
     * subsequent calls will retry the loader. This prevents permanent
     * negative caching on a transient miss.
     */
    public V get(String key, Function<String, V> loader) {
        V cached = entries.get(key);
        if (cached != null) {
            return cached;
        }
        V loaded = loader.apply(key);
        if (loaded != null) {
            entries.putIfAbsent(key, loaded);
        }
        return loaded;
    }

    /** Drops the entry for {@code key}. No-op if absent. */
    public void invalidate(String key) {
        entries.remove(key);
    }

    /** Drops every entry. Used for bulk-wildcard invalidations. */
    public void invalidateAll() {
        entries.clear();
    }

    /** Cache size — primarily for metrics / tests. */
    public int size() {
        return entries.size();
    }
}
