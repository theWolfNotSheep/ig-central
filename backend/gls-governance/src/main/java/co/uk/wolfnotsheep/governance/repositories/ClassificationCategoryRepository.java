package co.uk.wolfnotsheep.governance.repositories;

import co.uk.wolfnotsheep.governance.models.ClassificationCategory;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ClassificationCategoryRepository extends MongoRepository<ClassificationCategory, String> {

    List<ClassificationCategory> findByActiveTrue();

    List<ClassificationCategory> findByParentIdAndActiveTrue(String parentId);

    List<ClassificationCategory> findByParentIdIsNullAndActiveTrue();
}
