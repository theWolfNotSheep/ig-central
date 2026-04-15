package co.uk.wolfnotsheep.governance.repositories;

import co.uk.wolfnotsheep.governance.models.NodeTypeDefinition;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface NodeTypeDefinitionRepository extends MongoRepository<NodeTypeDefinition, String> {

    Optional<NodeTypeDefinition> findByKey(String key);

    List<NodeTypeDefinition> findByActiveTrueOrderByCategoryAscSortOrderAsc();
}
