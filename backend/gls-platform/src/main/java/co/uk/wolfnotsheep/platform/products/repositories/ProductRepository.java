package co.uk.wolfnotsheep.platform.products.repositories;

import co.uk.wolfnotsheep.platform.products.models.Product;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ProductRepository extends MongoRepository<Product, String> {

    List<Product> findByStatus(String status);
}
