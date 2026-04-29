package co.uk.wolfnotsheep.router.idempotency;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface IdempotencyRepository extends MongoRepository<IdempotencyRecord, String> {
}
