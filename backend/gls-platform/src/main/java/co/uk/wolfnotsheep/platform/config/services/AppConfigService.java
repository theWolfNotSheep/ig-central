package co.uk.wolfnotsheep.platform.config.services;

import co.uk.wolfnotsheep.platform.config.models.AppConfig;
import co.uk.wolfnotsheep.platform.config.repositories.AppConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AppConfigService {

    private static final Logger log = LoggerFactory.getLogger(AppConfigService.class);

    private final AppConfigRepository configRepo;
    private final Map<String, AppConfig> cache = new ConcurrentHashMap<>();

    public AppConfigService(AppConfigRepository configRepo) {
        this.configRepo = configRepo;
    }

    public void loadAll() {
        cache.clear();
        configRepo.findAll().forEach(c -> cache.put(c.getKey(), c));
        log.info("Loaded {} config entries", cache.size());
    }

    public Optional<AppConfig> get(String key) {
        AppConfig cached = cache.get(key);
        if (cached != null) return Optional.of(cached);
        Optional<AppConfig> fromDb = configRepo.findByKey(key);
        fromDb.ifPresent(c -> cache.put(c.getKey(), c));
        return fromDb;
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
        config.setKey(key);
        config.setCategory(category);
        config.setValue(value);
        config.setDescription(description);
        config.setUpdatedAt(Instant.now());
        AppConfig saved = configRepo.save(config);
        cache.put(key, saved);
        return saved;
    }

    public void refresh() {
        loadAll();
    }
}
