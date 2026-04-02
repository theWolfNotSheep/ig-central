package co.uk.wolfnotsheep.platform.identity.repositories;

import co.uk.wolfnotsheep.platform.identity.models.UserAccountType;
import co.uk.wolfnotsheep.platform.identity.models.UserModel;
import com.mongodb.lang.NonNull;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MongoUserRepository
        extends MongoRepository<UserModel, String> {

    Optional<UserModel> findByEmail(String email);

    boolean existsById(@NonNull String id);
    boolean existsByEmail(String email);

    Optional<UserModel> findByIdentityProviderAndIdentitySubject(String provider, String subject);

    List<UserModel> findByRolesContaining(String role);

    List<UserModel> findByAccountType(UserAccountType accountType);

    long countByAccountType(UserAccountType accountType);
    long countByCreatedDateAfter(LocalDateTime after);
}
