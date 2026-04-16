package co.uk.wolfnotsheep.hub.repositories;

import co.uk.wolfnotsheep.hub.models.HubUser;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface HubUserRepository extends MongoRepository<HubUser, String> {

    Optional<HubUser> findByUsername(String username);

    boolean existsByUsername(String username);
}
