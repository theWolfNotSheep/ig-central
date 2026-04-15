package co.uk.wolfnotsheep.hub.repositories;

import co.uk.wolfnotsheep.hub.models.PackReview;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PackReviewRepository extends MongoRepository<PackReview, String> {

    List<PackReview> findByPackIdOrderByCreatedAtDesc(String packId);

    Page<PackReview> findByPackIdOrderByCreatedAtDesc(String packId, Pageable pageable);
}
