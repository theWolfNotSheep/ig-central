package co.uk.wolfnotsheep.governance.repositories;

import co.uk.wolfnotsheep.governance.models.InstalledPack;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface InstalledPackRepository extends MongoRepository<InstalledPack, String> {
    Optional<InstalledPack> findByPackSlug(String packSlug);
}
