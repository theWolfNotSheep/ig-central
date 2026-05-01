package co.uk.wolfnotsheep.governance.repositories;

import co.uk.wolfnotsheep.governance.models.SensitivityDefinition;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface SensitivityDefinitionRepository extends MongoRepository<SensitivityDefinition, String> {

    List<SensitivityDefinition> findByActiveTrueOrderByLevelAsc();

    Optional<SensitivityDefinition> findByKey(String key);
}
