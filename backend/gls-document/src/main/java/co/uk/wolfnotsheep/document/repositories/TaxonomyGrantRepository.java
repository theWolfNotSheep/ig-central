package co.uk.wolfnotsheep.document.repositories;

import co.uk.wolfnotsheep.document.models.TaxonomyGrant;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface TaxonomyGrantRepository extends MongoRepository<TaxonomyGrant, String> {

    List<TaxonomyGrant> findByUserId(String userId);

    List<TaxonomyGrant> findByCategoryId(String categoryId);

    void deleteByUserIdAndCategoryId(String userId, String categoryId);
}
