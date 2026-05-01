package co.uk.wolfnotsheep.document.repositories;

import co.uk.wolfnotsheep.document.models.ConnectedDrive;
import co.uk.wolfnotsheep.document.models.StorageProviderType;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ConnectedDriveRepository extends MongoRepository<ConnectedDrive, String> {

    List<ConnectedDrive> findByUserIdAndActiveTrue(String userId);

    List<ConnectedDrive> findByUserId(String userId);

    Optional<ConnectedDrive> findByUserIdAndProviderAccountEmail(String userId, String email);

    Optional<ConnectedDrive> findBySystemDriveTrue();

    /** Find all drives accessible to a user: their own drives + system drives. */
    @Query("{ '$or': [ { 'userId': ?0, 'active': true }, { 'systemDrive': true, 'active': true } ] }")
    List<ConnectedDrive> findAccessibleDrives(String userId);

    List<ConnectedDrive> findByProviderTypeAndActiveTrue(StorageProviderType type);

    List<ConnectedDrive> findByMonitoredFolderIdsNotNullAndActiveTrue();

    Optional<ConnectedDrive> findByUserIdAndProviderAndProviderAccountEmail(String userId, String provider, String email);

    /** Find all Gmail/Drive accounts accessible to a user by provider type. */
    @Query("{ '$or': [ { 'userId': ?0, 'active': true, 'provider': ?1 }, { 'systemDrive': true, 'active': true, 'provider': ?1 } ] }")
    List<ConnectedDrive> findAccessibleByProvider(String userId, String provider);
}
