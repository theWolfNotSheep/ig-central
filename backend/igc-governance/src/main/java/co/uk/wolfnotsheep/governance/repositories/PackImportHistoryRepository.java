package co.uk.wolfnotsheep.governance.repositories;

import co.uk.wolfnotsheep.governance.models.PackImportHistory;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface PackImportHistoryRepository extends MongoRepository<PackImportHistory, String> {

    List<PackImportHistory> findByPackSlugOrderByImportedAtDesc(String packSlug);

    long countByPackSlug(String packSlug);
}
