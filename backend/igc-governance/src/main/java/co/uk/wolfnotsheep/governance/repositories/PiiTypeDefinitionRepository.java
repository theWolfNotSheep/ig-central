package co.uk.wolfnotsheep.governance.repositories;

import co.uk.wolfnotsheep.governance.models.PiiTypeDefinition;
import co.uk.wolfnotsheep.governance.models.PiiTypeDefinition.ApprovalStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface PiiTypeDefinitionRepository extends MongoRepository<PiiTypeDefinition, String> {

    List<PiiTypeDefinition> findByActiveTrueAndApprovalStatusOrderByCategoryAscDisplayNameAsc(
            ApprovalStatus status);

    List<PiiTypeDefinition> findByApprovalStatusOrderBySubmittedAtDesc(ApprovalStatus status);

    Optional<PiiTypeDefinition> findByKey(String key);
}
