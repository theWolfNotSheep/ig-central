package co.uk.wolfnotsheep.governance.repositories;

import co.uk.wolfnotsheep.governance.models.PipelineDefinition;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface PipelineDefinitionRepository extends MongoRepository<PipelineDefinition, String> {

    List<PipelineDefinition> findByActiveTrue();

    Optional<PipelineDefinition> findByName(String name);

    List<PipelineDefinition> findByActiveTrueAndApplicableCategoryIdsContaining(String categoryId);

    Optional<PipelineDefinition> findByActiveTrueAndIsDefaultTrue();
}
