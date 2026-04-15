package co.uk.wolfnotsheep.governance.repositories;

import co.uk.wolfnotsheep.governance.models.ClassificationCategory;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ClassificationCategoryRepository extends MongoRepository<ClassificationCategory, String> {

    Optional<ClassificationCategory> findByNameIgnoreCase(String name);

    List<ClassificationCategory> findByActiveTrue();

    List<ClassificationCategory> findByParentIdAndActiveTrue(String parentId);

    List<ClassificationCategory> findByParentIdIsNullAndActiveTrue();

    List<ClassificationCategory> findByMetadataSchemaId(String metadataSchemaId);
}
