package co.uk.wolfnotsheep.infrastructure.bootstrap;

import co.uk.wolfnotsheep.governance.models.ImportItemSnapshot;
import co.uk.wolfnotsheep.governance.repositories.ImportItemSnapshotRepository;
import co.uk.wolfnotsheep.governance.repositories.InstalledPackRepository;
import co.uk.wolfnotsheep.infrastructure.services.PackImportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;

/**
 * Backfills import_item_snapshots from current entity values for packs
 * that were imported before the snapshot feature existed. Uses current
 * entity state as the baseline — any future hub changes will diff cleanly.
 */
@Component
@Order(201)
public class ImportSnapshotBackfillRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ImportSnapshotBackfillRunner.class);

    private static final Map<String, String> COLLECTION_TO_TYPE = Map.of(
            "classification_categories", "TAXONOMY_CATEGORIES",
            "governance_policies", "GOVERNANCE_POLICIES",
            "retention_schedules", "RETENTION_SCHEDULES",
            "legislation", "LEGISLATION",
            "sensitivity_definitions", "SENSITIVITY_DEFINITIONS",
            "pii_type_definitions", "PII_TYPE_DEFINITIONS",
            "metadata_schemas", "METADATA_SCHEMAS",
            "storage_tiers", "STORAGE_TIERS",
            "trait_definitions", "TRAIT_DEFINITIONS"
    );

    private static final Map<String, String> TYPE_TO_KEY_FIELD = Map.of(
            "LEGISLATION", "key",
            "SENSITIVITY_DEFINITIONS", "key",
            "RETENTION_SCHEDULES", "name",
            "STORAGE_TIERS", "name",
            "METADATA_SCHEMAS", "name",
            "PII_TYPE_DEFINITIONS", "key",
            "TRAIT_DEFINITIONS", "key",
            "TAXONOMY_CATEGORIES", "name",
            "GOVERNANCE_POLICIES", "name"
    );

    private final ImportItemSnapshotRepository snapshotRepo;
    private final InstalledPackRepository installedPackRepo;
    private final MongoTemplate mongoTemplate;

    public ImportSnapshotBackfillRunner(ImportItemSnapshotRepository snapshotRepo,
                                        InstalledPackRepository installedPackRepo,
                                        MongoTemplate mongoTemplate) {
        this.snapshotRepo = snapshotRepo;
        this.installedPackRepo = installedPackRepo;
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (snapshotRepo.count() > 0) {
            return;
        }
        if (installedPackRepo.count() == 0) {
            return;
        }

        log.info("Backfilling import snapshots from current entity values...");
        int total = 0;

        for (Map.Entry<String, String> entry : COLLECTION_TO_TYPE.entrySet()) {
            String collection = entry.getKey();
            String componentType = entry.getValue();
            String keyField = TYPE_TO_KEY_FIELD.get(componentType);

            if (!mongoTemplate.collectionExists(collection)) continue;

            Query query = new Query(Criteria.where("sourcePackSlug").ne(null));
            List<Map> docs = mongoTemplate.find(query, Map.class, collection);

            for (Map doc : docs) {
                String slug = (String) doc.get("sourcePackSlug");
                if (slug == null || slug.isBlank()) continue;

                String itemKey = doc.get(keyField) != null ? doc.get(keyField).toString() : null;
                if (itemKey == null) continue;

                Object versionObj = doc.get("sourcePackVersion");
                int version = versionObj instanceof Number n ? n.intValue() : 1;

                String entityId = doc.get("_id") != null ? doc.get("_id").toString() : null;

                // Build snapshot from current entity values (stripping metadata)
                @SuppressWarnings("unchecked")
                Map<String, Object> fields = new LinkedHashMap<>((Map<String, Object>) doc);
                fields.remove("_id");
                fields.remove("_class");
                PackImportService.SNAPSHOT_EXCLUDED_FIELDS.forEach(fields::remove);

                ImportItemSnapshot snap = new ImportItemSnapshot();
                snap.setPackSlug(slug);
                snap.setPackVersion(version);
                snap.setComponentType(componentType);
                snap.setItemKey(itemKey);
                snap.setEntityId(entityId);
                snap.setSnapshotFields(fields);
                snap.setImportedAt(Instant.now());

                try {
                    snapshotRepo.save(snap);
                    total++;
                } catch (Exception e) {
                    log.debug("Skipping duplicate snapshot for {}/{}: {}", componentType, itemKey, e.getMessage());
                }
            }
        }

        if (total > 0) {
            log.info("Backfilled {} import snapshot(s)", total);
        }
    }
}
