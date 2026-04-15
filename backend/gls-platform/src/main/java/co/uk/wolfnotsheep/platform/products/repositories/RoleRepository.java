package co.uk.wolfnotsheep.platform.products.repositories;

import co.uk.wolfnotsheep.platform.products.models.Role;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface RoleRepository extends MongoRepository<Role, String> {

    Optional<Role> findByKey(String key);

    List<Role> findByStatus(String status);

    List<Role> findByIdIn(List<String> ids);

    List<Role> findByAccountTypeScopeContaining(String accountType);

    List<Role> findByAdminRoleTrueAndStatus(String status);

    List<Role> findByDefaultForNewUsersTrueAndStatus(String status);
}
