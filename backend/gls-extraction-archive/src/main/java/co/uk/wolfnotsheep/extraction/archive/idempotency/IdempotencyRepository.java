package co.uk.wolfnotsheep.extraction.archive.idempotency;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface IdempotencyRepository extends MongoRepository<IdempotencyRecord, String> {
}
