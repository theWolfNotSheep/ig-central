package co.uk.wolfnotsheep.platform.products.repositories;

import co.uk.wolfnotsheep.platform.products.models.Feature;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface FeatureRepository extends MongoRepository<Feature, String> {

    Optional<Feature> findByPermissionKey(String permissionKey);

    List<Feature> findByStatus(String status);

    List<Feature> findByCategory(String category);

    List<Feature> findByIdIn(List<String> ids);
}
