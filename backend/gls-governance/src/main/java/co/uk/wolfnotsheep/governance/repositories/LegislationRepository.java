package co.uk.wolfnotsheep.governance.repositories;

import co.uk.wolfnotsheep.governance.models.Legislation;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface LegislationRepository extends MongoRepository<Legislation, String> {

    Optional<Legislation> findByKey(String key);

    List<Legislation> findByActiveTrueOrderByNameAsc();

    List<Legislation> findByJurisdictionAndActiveTrue(String jurisdiction);
}
