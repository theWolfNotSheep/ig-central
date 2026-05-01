package co.uk.wolfnotsheep.platform.config.services;

import co.uk.wolfnotsheep.platform.config.models.AppConfig;
import co.uk.wolfnotsheep.platform.config.repositories.AppConfigRepository;
import co.uk.wolfnotsheep.platformconfig.cache.ConfigCache;
import co.uk.wolfnotsheep.platformconfig.cache.ConfigCacheRegistry;
import co.uk.wolfnotsheep.platformconfig.event.ChangeType;
import co.uk.wolfnotsheep.platformconfig.publish.ConfigChangePublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Reads and writes application config entries with a per-replica cache
 * fronting the Mongo repository.
 *
 * <p>Migrated to {@code igc-platform-config} per CSV #30 — cache is now a
 * {@link ConfigCache} that registers with the
 * {@link ConfigCacheRegistry}; mutations publish a {@code ConfigChangedEvent}
 * (entity type {@value #ENTITY_TYPE}) so every replica drops its stale
 * entries the moment the write commits, regardless of which replica took
 * the write.
 */
@Service
public class AppConfigService {

    /** Wire-protocol identifier for {@code app_config} entity changes. */
    public static final String ENTITY_TYPE = "APP_CONFIG";

    private static final Logger log = LoggerFactory.getLogger(AppConfigService.class);

    private final AppConfigRepository configRepo;
    private final ConfigCache<AppConfig> cache;
    private final ObjectProvider<ConfigChangePublisher> publisherProvider;

    public AppConfigService(
            AppConfigRepository configRepo,
            ConfigCacheRegistry cacheRegistry,
            ObjectProvider<ConfigChangePublisher> publisherProvider) {
        this.configRepo = configRepo;
        this.cache = new ConfigCache<>(ENTITY_TYPE);
        cacheRegistry.register(cache);
        this.publisherProvider = publisherProvider;
    }

    public Optional<AppConfig> get(String key) {
        AppConfig value = cache.get(key, k -> configRepo.findByKey(k).orElse(null));
        return Optional.ofNullable(value);
    }

    @SuppressWarnings("unchecked")
    public <T> T getValue(String key, T defaultValue) {
        return get(key)
                .map(c -> (T) c.getValue())
                .orElse(defaultValue);
    }

    public List<AppConfig> getByCategory(String category) {
        return configRepo.findByCategory(category);
    }

    public List<AppConfig> getAll() {
        return configRepo.findAll();
    }

    public AppConfig save(String key, String category, Object value, String description) {
        AppConfig config = configRepo.findByKey(key).orElseGet(AppConfig::new);
        ChangeType changeType = config.getId() == null ? ChangeType.CREATED : ChangeType.UPDATED;
        config.setKey(key);
        config.setCategory(category);
        config.setValue(value);
        config.setDescription(description);
        config.setUpdatedAt(Instant.now());
        AppConfig saved = configRepo.save(config);

        // Local-side invalidation is immediate; the publish causes peer
        // replicas to do the same when they receive the event. The next
        // get() on any replica re-loads fresh from Mongo.
        cache.invalidate(key);
        ConfigChangePublisher publisher = publisherProvider.getIfAvailable();
        if (publisher != null) {
            publisher.publishSingle(ENTITY_TYPE, key, changeType);
        }
        return saved;
    }

    /**
     * Drops every cached entry on this replica and tells peers to do the
     * same via a bulk {@code ConfigChangedEvent}. Manual admin trigger —
     * normal writes invalidate themselves.
     */
    public void refresh() {
        cache.invalidateAll();
        ConfigChangePublisher publisher = publisherProvider.getIfAvailable();
        if (publisher != null) {
            publisher.publishBulk(ENTITY_TYPE, ChangeType.UPDATED);
        }
        log.info("AppConfigService cache flushed; bulk invalidation broadcast (publisher={})",
                publisher != null ? "enabled" : "disabled");
    }
}
