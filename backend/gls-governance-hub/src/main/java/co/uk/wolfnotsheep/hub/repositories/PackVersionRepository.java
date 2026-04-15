package co.uk.wolfnotsheep.hub.repositories;

import co.uk.wolfnotsheep.hub.models.PackVersion;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PackVersionRepository extends MongoRepository<PackVersion, String> {

    List<PackVersion> findByPackIdOrderByVersionNumberDesc(String packId);

    Optional<PackVersion> findByPackIdAndVersionNumber(String packId, int versionNumber);
}
