package co.uk.wolfnotsheep.governance.repositories;

import co.uk.wolfnotsheep.governance.models.ClassificationCategory;
import co.uk.wolfnotsheep.governance.models.ClassificationCategory.NodeStatus;
import co.uk.wolfnotsheep.governance.models.ClassificationCategory.TaxonomyLevel;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ClassificationCategoryRepository extends MongoRepository<ClassificationCategory, String> {

    Optional<ClassificationCategory> findByNameIgnoreCase(String name);

    Optional<ClassificationCategory> findByClassificationCode(String code);

    boolean existsByClassificationCode(String code);

    // Status-based queries (replaces active-based)
    List<ClassificationCategory> findByStatus(NodeStatus status);

    List<ClassificationCategory> findByParentIdAndStatus(String parentId, NodeStatus status);

    List<ClassificationCategory> findByParentIdIsNullAndStatus(NodeStatus status);

    List<ClassificationCategory> findByLevelAndStatus(TaxonomyLevel level, NodeStatus status);

    // Jurisdiction queries
    List<ClassificationCategory> findByJurisdictionAndStatus(String jurisdiction, NodeStatus status);

    // Materialised path queries
    List<ClassificationCategory> findByPathContaining(String code);

    List<ClassificationCategory> findByMetadataSchemaId(String metadataSchemaId);

    // Backward-compatible convenience methods via default
    default List<ClassificationCategory> findByActiveTrue() {
        return findByStatus(NodeStatus.ACTIVE);
    }

    default List<ClassificationCategory> findByParentIdAndActiveTrue(String parentId) {
        return findByParentIdAndStatus(parentId, NodeStatus.ACTIVE);
    }

    default List<ClassificationCategory> findByParentIdIsNullAndActiveTrue() {
        return findByParentIdIsNullAndStatus(NodeStatus.ACTIVE);
    }
}
