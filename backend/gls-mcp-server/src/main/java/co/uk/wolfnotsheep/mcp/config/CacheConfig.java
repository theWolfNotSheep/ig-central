package co.uk.wolfnotsheep.mcp.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

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
     * Default cache: 200 entries max, 5 minute TTL.
     * All named caches share this configuration.
     * Short-lived caches (corrections, PII) are evicted more aggressively
     * via explicit @CacheEvict calls on admin writes.
     */
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(
                CACHE_TAXONOMY, CACHE_SENSITIVITIES, CACHE_TRAITS,
                CACHE_POLICIES, CACHE_RETENTION, CACHE_STORAGE, CACHE_SCHEMAS,
                CACHE_CORRECTIONS, CACHE_PII_PATTERNS);
        manager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(200)
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .recordStats());
        return manager;
    }
}
