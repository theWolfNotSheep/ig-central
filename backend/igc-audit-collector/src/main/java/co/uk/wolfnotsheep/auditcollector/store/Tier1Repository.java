package co.uk.wolfnotsheep.auditcollector.store;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface Tier1Repository extends MongoRepository<StoredTier1Event, String> {

    /**
     * Latest event in a per-resource chain — used for chain validation
     * (the {@code previousEventHash} of an incoming event must equal
     * the hash of this row).
     */
    Optional<StoredTier1Event> findFirstByResourceTypeAndResourceIdOrderByTimestampDesc(
            String resourceType, String resourceId);

    /**
     * Walk a resource's chain in order — used by
     * {@code GET /v1/chains/{resourceType}/{resourceId}/verify}.
     */
    List<StoredTier1Event> findByResourceTypeAndResourceIdOrderByTimestampAsc(
            String resourceType, String resourceId);
}
