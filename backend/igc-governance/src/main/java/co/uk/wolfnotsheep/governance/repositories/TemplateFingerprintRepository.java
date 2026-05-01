package co.uk.wolfnotsheep.governance.repositories;

import co.uk.wolfnotsheep.governance.models.TemplateFingerprint;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface TemplateFingerprintRepository extends MongoRepository<TemplateFingerprint, String> {

    Optional<TemplateFingerprint> findByFingerprint(String fingerprint);

    List<TemplateFingerprint> findByMimeType(String mimeType);

    long countByMimeType(String mimeType);
}
