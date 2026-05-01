package co.uk.wolfnotsheep.governance.repositories;

import co.uk.wolfnotsheep.governance.models.PackUpdateAvailable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface PackUpdateAvailableRepository extends MongoRepository<PackUpdateAvailable, String> {
    Optional<PackUpdateAvailable> findByPackSlug(String packSlug);
    List<PackUpdateAvailable> findByDismissedFalse();
    long countByDismissedFalse();
    void deleteByPackSlug(String packSlug);
}
