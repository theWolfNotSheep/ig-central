package co.uk.wolfnotsheep.governance.repositories;

import co.uk.wolfnotsheep.governance.models.BertTrainingJob;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface BertTrainingJobRepository extends MongoRepository<BertTrainingJob, String> {
    List<BertTrainingJob> findAllByOrderByStartedAtDesc();
    Optional<BertTrainingJob> findByModelVersion(String modelVersion);
    Optional<BertTrainingJob> findByPromotedTrue();
}
