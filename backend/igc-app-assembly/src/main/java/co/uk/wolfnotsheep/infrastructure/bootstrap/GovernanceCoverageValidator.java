package co.uk.wolfnotsheep.infrastructure.bootstrap;

import co.uk.wolfnotsheep.governance.models.ClassificationCategory;
import co.uk.wolfnotsheep.governance.models.MetadataSchema;
import co.uk.wolfnotsheep.governance.repositories.ClassificationCategoryRepository;
import co.uk.wolfnotsheep.governance.repositories.MetadataSchemaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Validates metadata schema coverage on startup.
 * Runs after GovernanceDataSeeder (@Order(2)) to catch configuration issues early.
 */
@Component
@Order(3)
public class GovernanceCoverageValidator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(GovernanceCoverageValidator.class);

    private final ClassificationCategoryRepository categoryRepository;
    private final MetadataSchemaRepository metadataSchemaRepository;

    public GovernanceCoverageValidator(ClassificationCategoryRepository categoryRepository,
                                       MetadataSchemaRepository metadataSchemaRepository) {
        this.categoryRepository = categoryRepository;
        this.metadataSchemaRepository = metadataSchemaRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<ClassificationCategory> categories = categoryRepository.findByActiveTrue();
        List<MetadataSchema> schemas = metadataSchemaRepository.findByActiveTrueOrderByNameAsc();

        Map<String, MetadataSchema> schemaById = schemas.stream()
                .collect(Collectors.toMap(MetadataSchema::getId, s -> s));

        Set<String> referencedSchemaIds = categories.stream()
                .map(ClassificationCategory::getMetadataSchemaId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());

        int warnings = 0;
        int errors = 0;

        // Categories without a metadata schema
        for (ClassificationCategory cat : categories) {
            if (cat.getMetadataSchemaId() == null) {
                log.warn("Category '{}' has no metadata schema assigned — documents classified here will have no structured metadata extracted.", cat.getName());
                warnings++;
            } else if (!schemaById.containsKey(cat.getMetadataSchemaId())) {
                log.error("Category '{}' references schema ID '{}' which does not exist or is inactive — metadata extraction will fail for this category.",
                        cat.getName(), cat.getMetadataSchemaId());
                errors++;
            }
        }

        // Schemas that no category references
        for (MetadataSchema schema : schemas) {
            if (!referencedSchemaIds.contains(schema.getId())) {
                log.error("Schema '{}' (id={}) is active but no categories reference it — it will never be used.",
                        schema.getName(), schema.getId());
                errors++;
            }
        }

        if (errors == 0 && warnings == 0) {
            log.info("Governance coverage check passed: {} schemas linked to {} categories.", schemas.size(), referencedSchemaIds.size());
        } else {
            log.info("Governance coverage check complete: {} warnings, {} errors.", warnings, errors);
        }
    }
}
