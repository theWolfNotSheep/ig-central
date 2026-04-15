package co.uk.wolfnotsheep.hub.repositories;

import co.uk.wolfnotsheep.hub.models.HubApiKey;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface HubApiKeyRepository extends MongoRepository<HubApiKey, String> {

    Optional<HubApiKey> findByKeyHash(String keyHash);

    List<HubApiKey> findByActiveTrue();
}
