package co.uk.wolfnotsheep.document.repositories;

import co.uk.wolfnotsheep.document.models.DirectoryRoleMapping;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface DirectoryRoleMappingRepository extends MongoRepository<DirectoryRoleMapping, String> {

    List<DirectoryRoleMapping> findByDirectorySourceAndActiveTrue(String directorySource);

    List<DirectoryRoleMapping> findByActiveTrue();
}
