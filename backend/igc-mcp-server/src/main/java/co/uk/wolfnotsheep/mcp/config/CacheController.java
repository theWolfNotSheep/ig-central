package co.uk.wolfnotsheep.mcp.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.CacheManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Internal endpoint for cache eviction.
 * Called by the API service after governance admin changes.
 */
@RestController
@RequestMapping("/api/internal/cache")
public class CacheController {

    private static final Logger log = LoggerFactory.getLogger(CacheController.class);

    private final CacheManager cacheManager;

    public CacheController(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    /**
     * Evict all entries from all caches.
     */
    @PostMapping("/evict-all")
    public ResponseEntity<Map<String, String>> evictAll() {
        cacheManager.getCacheNames().forEach(name -> {
            var cache = cacheManager.getCache(name);
            if (cache != null) cache.clear();
        });
        log.info("[Cache] All MCP caches evicted");
        return ResponseEntity.ok(Map.of("status", "evicted"));
    }

    /**
     * Evict a specific named cache.
     */
    @PostMapping("/evict/{cacheName}")
    public ResponseEntity<Map<String, String>> evictCache(@PathVariable String cacheName) {
        var cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.clear();
            log.info("[Cache] Evicted cache: {}", cacheName);
            return ResponseEntity.ok(Map.of("status", "evicted", "cache", cacheName));
        }
        return ResponseEntity.notFound().build();
    }
}
