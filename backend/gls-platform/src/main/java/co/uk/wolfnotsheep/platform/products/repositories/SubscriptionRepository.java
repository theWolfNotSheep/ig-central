package co.uk.wolfnotsheep.platform.products.repositories;

import co.uk.wolfnotsheep.platform.products.models.Subscription;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface SubscriptionRepository extends MongoRepository<Subscription, String> {

    List<Subscription> findByUserIdAndStatusIn(String userId, List<String> statuses);

    List<Subscription> findByCompanyIdAndStatusIn(String companyId, List<String> statuses);

    List<Subscription> findByUserId(String userId);

    List<Subscription> findByProductId(String productId);
}
