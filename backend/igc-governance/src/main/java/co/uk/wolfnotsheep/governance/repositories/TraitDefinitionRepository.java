package co.uk.wolfnotsheep.governance.repositories;

import co.uk.wolfnotsheep.governance.models.TraitDefinition;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface TraitDefinitionRepository extends MongoRepository<TraitDefinition, String> {

    List<TraitDefinition> findByActiveTrueOrderByDimensionAscDisplayNameAsc();

    List<TraitDefinition> findByDimensionAndActiveTrue(String dimension);

    Optional<TraitDefinition> findByKey(String key);
}
