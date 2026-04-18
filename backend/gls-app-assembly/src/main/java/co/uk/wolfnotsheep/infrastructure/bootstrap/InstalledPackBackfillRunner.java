package co.uk.wolfnotsheep.infrastructure.bootstrap;

import co.uk.wolfnotsheep.governance.models.InstalledPack;
import co.uk.wolfnotsheep.governance.repositories.InstalledPackRepository;
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
 * Backfills the installed_packs collection from sourcePackSlug provenance
 * on existing governance models. Only runs if installed_packs is empty.
 */
@Component
@Order(200)
public class InstalledPackBackfillRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(InstalledPackBackfillRunner.class);

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

    private final InstalledPackRepository installedPackRepo;
    private final MongoTemplate mongoTemplate;

    public InstalledPackBackfillRunner(InstalledPackRepository installedPackRepo,
                                       MongoTemplate mongoTemplate) {
        this.installedPackRepo = installedPackRepo;
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (installedPackRepo.count() > 0) {
            return;
        }

        log.info("Backfilling installed_packs from governance model provenance...");

        // slug -> { maxVersion, importedAt, componentTypes }
        Map<String, PackInfo> packs = new LinkedHashMap<>();

        for (Map.Entry<String, String> entry : COLLECTION_TO_TYPE.entrySet()) {
            String collection = entry.getKey();
            String componentType = entry.getValue();

            if (!mongoTemplate.collectionExists(collection)) continue;

            Query query = new Query(Criteria.where("sourcePackSlug").ne(null));
            List<Map> docs = mongoTemplate.find(query, Map.class, collection);

            for (Map doc : docs) {
                String slug = (String) doc.get("sourcePackSlug");
                if (slug == null || slug.isBlank()) continue;

                Object versionObj = doc.get("sourcePackVersion");
                int version = versionObj instanceof Number n ? n.intValue() : 1;

                Object importedObj = doc.get("importedAt");
                Instant importedAt = importedObj instanceof Date d ? d.toInstant() : Instant.now();

                PackInfo info = packs.computeIfAbsent(slug, k -> new PackInfo());
                if (version > info.maxVersion) {
                    info.maxVersion = version;
                    info.importedAt = importedAt;
                }
                info.componentTypes.add(componentType);
            }
        }

        int count = 0;
        for (Map.Entry<String, PackInfo> entry : packs.entrySet()) {
            String slug = entry.getKey();
            PackInfo info = entry.getValue();

            InstalledPack installed = new InstalledPack();
            installed.setPackSlug(slug);
            installed.setPackName(slug);
            installed.setInstalledVersion(info.maxVersion);
            installed.setImportedAt(info.importedAt);
            installed.setComponentTypesImported(new ArrayList<>(info.componentTypes));
            installedPackRepo.save(installed);
            count++;
        }

        if (count > 0) {
            log.info("Backfilled {} installed pack(s) from provenance data", count);
        }
    }

    private static class PackInfo {
        int maxVersion = 0;
        Instant importedAt = Instant.now();
        Set<String> componentTypes = new LinkedHashSet<>();
    }
}
