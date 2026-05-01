package co.uk.wolfnotsheep.indexing.quarantine;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface QuarantineRepository extends MongoRepository<QuarantineRecord, String> {
}
