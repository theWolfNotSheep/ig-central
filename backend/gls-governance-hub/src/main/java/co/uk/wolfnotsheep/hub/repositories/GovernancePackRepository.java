package co.uk.wolfnotsheep.hub.repositories;

import co.uk.wolfnotsheep.hub.models.GovernancePack;
import co.uk.wolfnotsheep.hub.models.GovernancePack.PackStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GovernancePackRepository extends MongoRepository<GovernancePack, String> {

    Optional<GovernancePack> findBySlug(String slug);

    Page<GovernancePack> findByStatusAndFeaturedTrue(PackStatus status, Pageable pageable);

    Page<GovernancePack> findByJurisdictionAndStatus(String jurisdiction, PackStatus status, Pageable pageable);

    Page<GovernancePack> findByStatus(PackStatus status, Pageable pageable);
}
