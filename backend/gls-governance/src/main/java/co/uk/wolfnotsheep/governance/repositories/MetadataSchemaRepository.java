package co.uk.wolfnotsheep.governance.repositories;

import co.uk.wolfnotsheep.governance.models.MetadataSchema;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface MetadataSchemaRepository extends MongoRepository<MetadataSchema, String> {

    List<MetadataSchema> findByActiveTrueOrderByNameAsc();

    Optional<MetadataSchema> findByName(String name);

    List<MetadataSchema> findByLinkedMimeTypesContainingAndActiveTrue(String mimeType);
}
