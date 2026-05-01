package co.uk.wolfnotsheep.infrastructure.migrations;

import com.mongodb.client.result.UpdateResult;
import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.Collections;
import java.util.List;

/**
 * Phase 1.7 / CSV #31–34. The four hub-component entities
 * ({@code pii_type_definitions}, {@code storage_tiers},
 * {@code trait_definitions}, {@code sensitivity_definitions}) gain an
 * {@code applicableCategoryIds[]} field. Empty array = global (the
 * pre-1.7 default behaviour). This migration sets the field to an
 * empty array on every existing row that doesn't already have it, so
 * the new field reads cleanly without nulls in production data.
 *
 * <p>Idempotent: a re-run only updates rows that still don't have the
 * field, which is zero after the first execution. The Mongo
 * {@code $exists: false} filter makes the update a no-op once applied.
 *
 * <p>Operates on raw collections (no Java domain shape imported)
 * — same decoupling pattern as {@code V003_DefaultRouterBlock}.
 */
@ChangeUnit(id = "backfill-applicable-category-ids", order = "004", author = "ig-central")
public class V004_BackfillApplicableCategoryIds {

    private static final Logger log = LoggerFactory.getLogger(V004_BackfillApplicableCategoryIds.class);

    private static final List<String> COLLECTIONS = List.of(
            "pii_type_definitions",
            "storage_tiers",
            "trait_definitions",
            "sensitivity_definitions"
    );

    @Execution
    public void execution(MongoTemplate mongoTemplate) {
        for (String collection : COLLECTIONS) {
            Document filter = new Document("applicableCategoryIds", new Document("$exists", false));
            Document update = new Document("$set", new Document("applicableCategoryIds", Collections.emptyList()));
            UpdateResult result = mongoTemplate.getCollection(collection).updateMany(filter, update);
            log.info("backfilled applicableCategoryIds=[] on {}.{} matched={} modified={}",
                    mongoTemplate.getDb().getName(), collection,
                    result.getMatchedCount(), result.getModifiedCount());
        }
    }

    @RollbackExecution
    public void rollbackExecution(MongoTemplate mongoTemplate) {
        // Conservative rollback: remove the field on rows whose
        // value is still the empty array we set. Any row whose
        // applicableCategoryIds was edited post-backfill is left
        // alone — operator data wins over migration rollback.
        for (String collection : COLLECTIONS) {
            Document filter = new Document("applicableCategoryIds", Collections.emptyList());
            Document update = new Document("$unset", new Document("applicableCategoryIds", ""));
            UpdateResult result = mongoTemplate.getCollection(collection).updateMany(filter, update);
            log.info("rollback unset applicableCategoryIds on {}.{} matched={} modified={}",
                    mongoTemplate.getDb().getName(), collection,
                    result.getMatchedCount(), result.getModifiedCount());
        }
    }
}
