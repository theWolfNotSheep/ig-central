package co.uk.wolfnotsheep.platform.config.repositories;

import co.uk.wolfnotsheep.platform.config.models.AppConfig;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface AppConfigRepository extends MongoRepository<AppConfig, String> {

    Optional<AppConfig> findByKey(String key);

    List<AppConfig> findByCategory(String category);

    boolean existsByKey(String key);
}
