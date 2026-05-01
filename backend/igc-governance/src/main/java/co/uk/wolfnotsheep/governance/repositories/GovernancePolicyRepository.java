package co.uk.wolfnotsheep.governance.repositories;

import co.uk.wolfnotsheep.governance.models.GovernancePolicy;
import co.uk.wolfnotsheep.governance.models.SensitivityLabel;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;

public interface GovernancePolicyRepository extends MongoRepository<GovernancePolicy, String> {

    List<GovernancePolicy> findByActiveTrue();

    List<GovernancePolicy> findByActiveTrueAndEffectiveFromBeforeAndEffectiveUntilAfter(
            Instant now1, Instant now2);

    List<GovernancePolicy> findByApplicableCategoryIdsContaining(String categoryId);

    List<GovernancePolicy> findByApplicableSensitivitiesContaining(SensitivityLabel label);
}
