package co.uk.wolfnotsheep.platformconfig.cache;

import co.uk.wolfnotsheep.platformconfig.event.ConfigChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Routing table from entity-type identifier to the {@link ConfigCache}
 * instances responsible for that type. Multiple caches may register for
 * the same {@code entityType} — every match is invalidated.
 *
 * <p>Caches normally register themselves on construction (or the
 * consumer wires them up in a {@code @Configuration}); the registry
 * lives as a singleton bean so the
 * {@link co.uk.wolfnotsheep.platformconfig.listen.ConfigChangeDispatcher}
 * can find them.
 */
public class ConfigCacheRegistry {

    private static final Logger log = LoggerFactory.getLogger(ConfigCacheRegistry.class);

    private final Map<String, List<ConfigCache<?>>> cachesByType = new ConcurrentHashMap<>();

    public void register(ConfigCache<?> cache) {
        cachesByType
                .computeIfAbsent(cache.entityType(), k -> new CopyOnWriteArrayList<>())
                .add(cache);
        log.debug("config cache registered: entityType={}, total registered for type={}",
                cache.entityType(), cachesByType.get(cache.entityType()).size());
    }

    /**
     * Apply an invalidation event to every cache registered for its
     * {@code entityType}. Bulk events ({@code entityIds} empty) trigger
     * {@link ConfigCache#invalidateAll()}; targeted events invalidate
     * each id one at a time.
     */
    public void dispatch(ConfigChangedEvent event) {
        List<ConfigCache<?>> matched = cachesByType.get(event.entityType());
        if (matched == null || matched.isEmpty()) {
            log.trace("config cache: no registered caches for entityType={}, dropping event",
                    event.entityType());
            return;
        }
        if (event.isBulk()) {
            for (ConfigCache<?> cache : matched) {
                cache.invalidateAll();
            }
            log.debug("config cache: bulk-invalidated {} cache(s) for entityType={}",
                    matched.size(), event.entityType());
            return;
        }
        for (ConfigCache<?> cache : matched) {
            for (String id : event.entityIds()) {
                cache.invalidate(id);
            }
        }
        log.debug("config cache: invalidated {} id(s) across {} cache(s) for entityType={}",
                event.entityIds().size(), matched.size(), event.entityType());
    }

    /** Test-only — number of caches registered for an entity type. */
    public int registrationCount(String entityType) {
        List<ConfigCache<?>> matched = cachesByType.get(entityType);
        return matched == null ? 0 : matched.size();
    }
}
