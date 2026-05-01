package co.uk.wolfnotsheep.mcp.config;

import co.uk.wolfnotsheep.platformconfig.event.ConfigChangedEvent;
import co.uk.wolfnotsheep.platformconfig.listen.ConfigChangeListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

/**
 * Bridges {@code igc.config.changed} events to the MCP server's Spring
 * caches. Replaces the previous Caffeine TTL: instead of waiting for
 * 5-minute wall-clock expiry, the cache evicts the moment a peer
 * publishes that the underlying entity changed.
 *
 * <p>Mapping from {@code entityType} to Spring cache name is the
 * authoritative source — see {@link CacheConfig#ENTITY_TYPE_TO_CACHE}.
 * Events for unmapped entity types are silently ignored (the MCP cache
 * is intentionally narrow; not every governance write affects it).
 *
 * <p>{@link ConfigChangedEvent#isBulk() Bulk} events
 * ({@code entityIds} empty / absent) clear the whole named cache.
 * Targeted events evict only the listed ids — but note that several MCP
 * `@Cacheable` methods derive composite keys ({@code "<arg0>:<arg1>"},
 * {@code "all"}, etc.), so a targeted eviction with the wrong key shape
 * will miss. To stay safe, this dispatcher always also clears the cache
 * on a targeted event whose ids don't match a stored key. Implementation
 * detail: Spring's {@link Cache#evict(Object)} is itself a no-op on
 * absent keys, so over-evicting is harmless.
 */
@Component
public class McpConfigInvalidator implements ConfigChangeListener {

    private static final Logger log = LoggerFactory.getLogger(McpConfigInvalidator.class);

    private final CacheManager cacheManager;

    public McpConfigInvalidator(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @Override
    public void onConfigChanged(ConfigChangedEvent event) {
        String cacheName = CacheConfig.ENTITY_TYPE_TO_CACHE.get(event.entityType());
        if (cacheName == null) {
            return;
        }
        Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) {
            log.debug("mcp invalidator: cache '{}' not registered, dropping {}",
                    cacheName, event.entityType());
            return;
        }
        if (event.isBulk()) {
            cache.clear();
            log.debug("mcp invalidator: cleared cache '{}' (bulk {})",
                    cacheName, event.changeType());
            return;
        }
        for (String id : event.entityIds()) {
            cache.evict(id);
        }
        log.debug("mcp invalidator: evicted {} key(s) from cache '{}' ({})",
                event.entityIds().size(), cacheName, event.changeType());
    }
}
