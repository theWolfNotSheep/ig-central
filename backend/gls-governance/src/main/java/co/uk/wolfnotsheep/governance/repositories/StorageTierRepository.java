package co.uk.wolfnotsheep.governance.repositories;

import co.uk.wolfnotsheep.governance.models.SensitivityLabel;
import co.uk.wolfnotsheep.governance.models.StorageTier;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface StorageTierRepository extends MongoRepository<StorageTier, String> {

    List<StorageTier> findByAllowedSensitivitiesContaining(SensitivityLabel label);
}
