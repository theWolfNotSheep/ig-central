package co.uk.wolfnotsheep.governance.repositories;

import co.uk.wolfnotsheep.governance.models.ImportItemSnapshot;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ImportItemSnapshotRepository extends MongoRepository<ImportItemSnapshot, String> {
    Optional<ImportItemSnapshot> findByPackSlugAndComponentTypeAndItemKey(String packSlug, String componentType, String itemKey);
    List<ImportItemSnapshot> findAllByPackSlug(String packSlug);
    List<ImportItemSnapshot> findAllByPackSlugAndComponentType(String packSlug, String componentType);
    void deleteAllByPackSlug(String packSlug);
}
