package co.uk.wolfnotsheep.governance.repositories;

import co.uk.wolfnotsheep.governance.models.RetentionSchedule;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface RetentionScheduleRepository extends MongoRepository<RetentionSchedule, String> {
}
