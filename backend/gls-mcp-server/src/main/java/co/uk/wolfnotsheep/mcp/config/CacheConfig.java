package co.uk.wolfnotsheep.mcp.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * Caffeine-backed cache for MCP governance tool responses.
 *
 * <p>Per CSV #30, the previous TTL has been replaced with change-driven
 * invalidation: every entry stays cached until either the bounded
 * Caffeine eviction trims it (memory ceiling, not staleness) or a
 * {@code gls.config.changed} event for the matching entity type lands.
 * The {@link McpConfigInvalidator} consumer listens for those events
 * and evicts via Spring's {@link CacheManager} surface.
 *
 * <p>Caffeine remains as the storage substrate — that's an
 * implementation detail. The contract is the change-driven refresh.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String CACHE_TAXONOMY = "taxonomy";
    public static final String CACHE_SENSITIVITIES = "sensitivities";
    public static final String CACHE_TRAITS = "traits";
    public static final String CACHE_POLICIES = "policies";
    public static final String CACHE_RETENTION = "retention";
    public static final String CACHE_STORAGE = "storage";
    public static final String CACHE_SCHEMAS = "schemas";
    public static final String CACHE_CORRECTIONS = "corrections";
    public static final String CACHE_PII_PATTERNS = "piiPatterns";

    /**
     * Maps governance entity types (the wire-protocol identifier on
     * {@link co.uk.wolfnotsheep.platformconfig.event.ConfigChangedEvent#entityType()})
     * to the Spring cache name that holds derived data for that entity
     * type. {@link McpConfigInvalidator} uses this when an event lands.
     */
    public static final Map<String, String> ENTITY_TYPE_TO_CACHE = Map.ofEntries(
            Map.entry("TAXONOMY", CACHE_TAXONOMY),
            Map.entry("SENSITIVITY", CACHE_SENSITIVITIES),
            Map.entry("TRAIT", CACHE_TRAITS),
            Map.entry("POLICY", CACHE_POLICIES),
            Map.entry("RETENTION_SCHEDULE", CACHE_RETENTION),
            Map.entry("STORAGE_TIER", CACHE_STORAGE),
            Map.entry("METADATA_SCHEMA", CACHE_SCHEMAS),
            Map.entry("CORRECTION", CACHE_CORRECTIONS),
            Map.entry("PII_PATTERN_SET", CACHE_PII_PATTERNS));

    /**
     * 200-entry ceiling for memory, no TTL. Staleness now flows from
     * {@code gls.config.changed} events, not wall-clock expiry.
     */
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(
                CACHE_TAXONOMY, CACHE_SENSITIVITIES, CACHE_TRAITS,
                CACHE_POLICIES, CACHE_RETENTION, CACHE_STORAGE, CACHE_SCHEMAS,
                CACHE_CORRECTIONS, CACHE_PII_PATTERNS);
        manager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(200)
                .recordStats());
        return manager;
    }
}
