package co.uk.wolfnotsheep.infrastructure.migrations;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Seeds the default ROUTER block so the cascade router has a stable
 * coordinate to resolve when the orchestrator cutover (Phase 1.3)
 * starts pinning blocks. Per the Phase 1.2 plan: cascade defaults to
 * {@code bertAccept = 1.01} everywhere — every result falls through
 * to the next tier, so the cascade is functionally disabled until
 * tuning lands. The schema is
 * {@code contracts/blocks/router.schema.json} (v0.2.0).
 *
 * <p>Idempotent: skips the insert when a block with name
 * {@code default-router} already exists. Re-running the unit is a
 * no-op.
 *
 * <p>Mongock declarative shape (no Java domain model imported here)
 * — the change unit operates on the raw {@code pipeline_blocks}
 * collection so it stays decoupled from any future shape change to
 * the Java {@code PipelineBlock} class.
 */
@ChangeUnit(id = "default-router-block", order = "003", author = "ig-central")
public class V003_DefaultRouterBlock {

    private static final Logger log = LoggerFactory.getLogger(V003_DefaultRouterBlock.class);
    private static final String COLLECTION = "pipeline_blocks";
    private static final String BLOCK_NAME = "default-router";

    @Execution
    public void execution(MongoTemplate mongoTemplate) {
        Query existing = Query.query(Criteria.where("name").is(BLOCK_NAME));
        if (mongoTemplate.exists(existing, COLLECTION)) {
            log.info("default ROUTER block already exists — skipping seed");
            return;
        }

        Map<String, Object> tierConservative = Map.of(
                "enabled", true,
                "accept", 1.01);

        Map<String, Object> content = Map.of(
                "tiers", Map.of(
                        "bert", tierConservative,
                        "slm", tierConservative,
                        "llm", Map.of("enabled", true, "accept", 0.0)),
                "fallback", Map.of("strategy", "LLM_FLOOR"));

        Instant now = Instant.now();
        Document version = new Document()
                .append("version", 1)
                .append("content", content)
                .append("changelog", "Initial cascade-disabled defaults; tuning lands per category in 1.4–1.6.")
                .append("publishedBy", "ig-central")
                .append("publishedAt", now);

        Document block = new Document()
                .append("name", BLOCK_NAME)
                .append("description", "Default cascade policy. bertAccept=1.01, slmAccept=1.01, llmAccept=0.0 — every request falls through to the LLM tier until per-category tuning is applied. Schema: contracts/blocks/router.schema.json v0.2.0.")
                .append("type", "ROUTER")
                .append("active", true)
                .append("activeVersion", 1)
                .append("versions", List.of(version))
                .append("documentsProcessed", 0L)
                .append("correctionsReceived", 0L)
                .append("feedbackCount", 0L)
                .append("createdAt", now)
                .append("createdBy", "mongock:V003_DefaultRouterBlock")
                .append("updatedAt", now);

        mongoTemplate.getCollection(COLLECTION).insertOne(block);
        log.info("default ROUTER block seeded — name={}, activeVersion=1", BLOCK_NAME);
    }

    @RollbackExecution
    public void rollbackExecution(MongoTemplate mongoTemplate) {
        Query byName = Query.query(Criteria.where("name").is(BLOCK_NAME)
                .and("createdBy").is("mongock:V003_DefaultRouterBlock"));
        long deleted = mongoTemplate.remove(byName, COLLECTION).getDeletedCount();
        log.info("default ROUTER block rollback removed {} document(s)", deleted);
    }
}
