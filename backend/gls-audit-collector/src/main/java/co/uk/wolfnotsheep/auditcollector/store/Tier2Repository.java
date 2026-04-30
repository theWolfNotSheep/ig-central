package co.uk.wolfnotsheep.auditcollector.store;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface Tier2Repository extends MongoRepository<StoredTier2Event, String> {
}
