package co.uk.wolfnotsheep.infrastructure.controllers.documents;

import co.uk.wolfnotsheep.document.models.DocumentModel;
import co.uk.wolfnotsheep.document.models.DocumentStatus;
import co.uk.wolfnotsheep.infrastructure.services.DocumentAccessService;
import co.uk.wolfnotsheep.governance.models.ClassificationCategory;
import co.uk.wolfnotsheep.governance.models.MetadataSchema;
import co.uk.wolfnotsheep.governance.models.SensitivityLabel;
import co.uk.wolfnotsheep.governance.repositories.ClassificationCategoryRepository;
import co.uk.wolfnotsheep.governance.services.GovernanceService;
import co.uk.wolfnotsheep.platform.identity.models.UserModel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Advanced search endpoint with dynamic metadata filtering.
 * When a category is selected, the UI loads its metadata schema
 * and renders filter fields dynamically. Those filters query
 * extractedMetadata.{fieldName} on the document.
 */
@RestController
@RequestMapping("/api/search")
public class SearchController {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SearchController.class);

    private final MongoTemplate mongoTemplate;
    private final GovernanceService governanceService;
    private final DocumentAccessService accessService;
    private final ClassificationCategoryRepository categoryRepository;

    public SearchController(MongoTemplate mongoTemplate, GovernanceService governanceService,
                            DocumentAccessService accessService,
                            ClassificationCategoryRepository categoryRepository) {
        this.mongoTemplate = mongoTemplate;
        this.governanceService = governanceService;
        this.accessService = accessService;
        this.categoryRepository = categoryRepository;
    }

    @PostMapping
    public ResponseEntity<Page<DocumentModel>> search(
            @RequestBody SearchRequest request,
            @AuthenticationPrincipal UserDetails user,
            Pageable pageable) {

        List<Criteria> filters = new ArrayList<>();

        // ACCESS CONTROL — apply user's taxonomy + sensitivity filter
        if (user instanceof UserModel um) {
            filters.add(accessService.buildAccessCriteria(um));
        }

        // Full-text search across filename, category, tags, extracted text
        if (request.q() != null && !request.q().isBlank()) {
            String pattern = ".*" + java.util.regex.Pattern.quote(request.q().trim()) + ".*";
            filters.add(new Criteria().orOperator(
                    Criteria.where("originalFileName").regex(pattern, "i"),
                    Criteria.where("categoryName").regex(pattern, "i"),
                    Criteria.where("classificationCode").regex(pattern, "i"),
                    Criteria.where("tags").regex(pattern, "i"),
                    Criteria.where("extractedText").regex(pattern, "i")
            ));
        }

        // Standard filters
        if (request.status() != null && !request.status().isBlank()) {
            filters.add(Criteria.where("status").is(DocumentStatus.valueOf(request.status())));
        }
        if (request.sensitivity() != null && !request.sensitivity().isBlank()) {
            filters.add(Criteria.where("sensitivityLabel").is(SensitivityLabel.valueOf(request.sensitivity())));
        }
        if (request.categoryId() != null && !request.categoryId().isBlank()) {
            filters.add(Criteria.where("categoryId").is(request.categoryId()));
        }
        if (request.categoryName() != null && !request.categoryName().isBlank()) {
            filters.add(Criteria.where("categoryName").is(request.categoryName()));
        }
        if (request.mimeType() != null && !request.mimeType().isBlank()) {
            filters.add(Criteria.where("mimeType").regex(
                    ".*" + java.util.regex.Pattern.quote(request.mimeType()) + ".*", "i"));
        }
        if (request.uploadedBy() != null && !request.uploadedBy().isBlank()) {
            filters.add(Criteria.where("uploadedBy").regex(
                    ".*" + java.util.regex.Pattern.quote(request.uploadedBy()) + ".*", "i"));
        }

        // Classification code filters
        if (request.classificationCode() != null && !request.classificationCode().isBlank()) {
            filters.add(Criteria.where("classificationCode").is(request.classificationCode()));
        }
        if (request.classificationCodePrefix() != null && !request.classificationCodePrefix().isBlank()) {
            filters.add(Criteria.where("classificationCode").regex(
                    "^" + java.util.regex.Pattern.quote(request.classificationCodePrefix()), "i"));
        }
        if (request.classificationLevel() != null && !request.classificationLevel().isBlank()) {
            filters.add(Criteria.where("classificationLevel").is(request.classificationLevel()));
        }

        // Date range
        if (request.createdAfter() != null) {
            filters.add(Criteria.where("createdAt").gte(Instant.parse(request.createdAfter())));
        }
        if (request.createdBefore() != null) {
            filters.add(Criteria.where("createdAt").lte(Instant.parse(request.createdBefore())));
        }

        // Dynamic metadata filters — query extractedMetadata.{key}
        if (request.metadata() != null && !request.metadata().isEmpty()) {
            for (var entry : request.metadata().entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                if (value == null || value.isBlank()) continue;

                String fieldPath = "extractedMetadata." + key;
                // Support prefix matching for partial searches
                if (value.contains("*")) {
                    String pattern = value.replace("*", ".*");
                    filters.add(Criteria.where(fieldPath).regex(pattern, "i"));
                } else {
                    filters.add(Criteria.where(fieldPath).regex(
                            ".*" + java.util.regex.Pattern.quote(value) + ".*", "i"));
                }
            }
        }

        // Exclude disposed documents by default
        if (request.status() == null || request.status().isBlank()) {
            filters.add(Criteria.where("status").ne(DocumentStatus.DISPOSED));
        }

        Query query = filters.isEmpty()
                ? new Query().with(pageable)
                : new Query(new Criteria().andOperator(filters.toArray(new Criteria[0]))).with(pageable);

        List<DocumentModel> results = mongoTemplate.find(query, DocumentModel.class);
        return ResponseEntity.ok(PageableExecutionUtils.getPage(results, pageable,
                () -> mongoTemplate.count(Query.of(query).limit(-1).skip(-1), DocumentModel.class)));
    }

    /**
     * Get facet counts for building filter UI.
     * Accepts optional filters so counts are scoped to the current selection.
     * e.g. when a category is selected, status/sensitivity counts reflect only
     * documents in that category.
     */
    @GetMapping("/facets")
    public ResponseEntity<Map<String, Object>> getFacets(
            @RequestParam(required = false) String categoryName,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String sensitivity,
            @AuthenticationPrincipal UserDetails user) {

        // Build base criteria from current filters
        List<Criteria> baseFilters = new ArrayList<>();
        baseFilters.add(Criteria.where("status").ne(DocumentStatus.DISPOSED));

        // ACCESS CONTROL — apply user's access filter to facet counts
        if (user instanceof UserModel um) {
            baseFilters.add(accessService.buildAccessCriteria(um));
        }
        if (categoryName != null && !categoryName.isBlank()) {
            baseFilters.add(Criteria.where("categoryName").is(categoryName));
        }
        if (status != null && !status.isBlank()) {
            baseFilters.add(Criteria.where("status").is(DocumentStatus.valueOf(status)));
        }
        if (sensitivity != null && !sensitivity.isBlank()) {
            baseFilters.add(Criteria.where("sensitivityLabel").is(SensitivityLabel.valueOf(sensitivity)));
        }

        // Category counts — aggregation pipeline (no full scan)
        Map<String, Long> byCat = new java.util.LinkedHashMap<>();
        try {
            var catPipeline = org.springframework.data.mongodb.core.aggregation.Aggregation.newAggregation(
                    org.springframework.data.mongodb.core.aggregation.Aggregation.match(Criteria.where("status").ne("DISPOSED").and("categoryName").ne(null)),
                    org.springframework.data.mongodb.core.aggregation.Aggregation.group("categoryName").count().as("count"),
                    org.springframework.data.mongodb.core.aggregation.Aggregation.sort(org.springframework.data.domain.Sort.Direction.DESC, "count")
            );
            mongoTemplate.aggregate(catPipeline, "documents", org.bson.Document.class)
                    .getMappedResults().forEach(d -> byCat.put(d.getString("_id"), d.get("count", Number.class).longValue()));
        } catch (Exception e) { log.warn("Category aggregation failed: {}", e.getMessage()); }

        // Status and sensitivity counts — aggregation on filtered criteria
        Criteria filterCriteria = baseFilters.isEmpty() ? new Criteria() : new Criteria().andOperator(baseFilters.toArray(new Criteria[0]));

        Map<String, Long> bySensitivity = new java.util.LinkedHashMap<>();
        try {
            var sensPipeline = org.springframework.data.mongodb.core.aggregation.Aggregation.newAggregation(
                    org.springframework.data.mongodb.core.aggregation.Aggregation.match(filterCriteria),
                    org.springframework.data.mongodb.core.aggregation.Aggregation.match(Criteria.where("sensitivityLabel").ne(null)),
                    org.springframework.data.mongodb.core.aggregation.Aggregation.group("sensitivityLabel").count().as("count")
            );
            mongoTemplate.aggregate(sensPipeline, "documents", org.bson.Document.class)
                    .getMappedResults().forEach(d -> bySensitivity.put(d.getString("_id"), d.get("count", Number.class).longValue()));
        } catch (Exception e) { log.warn("Sensitivity aggregation failed: {}", e.getMessage()); }

        Map<String, Long> byStatus = new java.util.LinkedHashMap<>();
        try {
            var statusPipeline = org.springframework.data.mongodb.core.aggregation.Aggregation.newAggregation(
                    org.springframework.data.mongodb.core.aggregation.Aggregation.match(filterCriteria),
                    org.springframework.data.mongodb.core.aggregation.Aggregation.group("status").count().as("count")
            );
            mongoTemplate.aggregate(statusPipeline, "documents", org.bson.Document.class)
                    .getMappedResults().forEach(d -> byStatus.put(d.getString("_id"), d.get("count", Number.class).longValue()));
        } catch (Exception e) { log.warn("Status aggregation failed: {}", e.getMessage()); }

        long totalFiltered = byStatus.values().stream().mapToLong(Long::longValue).sum();

        // Available metadata schemas
        List<Map<String, Object>> schemas = governanceService.getActiveMetadataSchemas().stream()
                .map(s -> {
                    List<String> catIds = categoryRepository.findByMetadataSchemaId(s.getId()).stream()
                            .map(ClassificationCategory::getId).toList();
                    return Map.<String, Object>of(
                            "id", s.getId(),
                            "name", s.getName(),
                            "fields", s.getFields() != null ? s.getFields() : List.of(),
                            "linkedCategoryIds", catIds
                    );
                })
                .toList();

        // Classification code counts
        Map<String, Long> byCode = new java.util.LinkedHashMap<>();
        try {
            var codePipeline = org.springframework.data.mongodb.core.aggregation.Aggregation.newAggregation(
                    org.springframework.data.mongodb.core.aggregation.Aggregation.match(Criteria.where("status").ne("DISPOSED").and("classificationCode").ne(null)),
                    org.springframework.data.mongodb.core.aggregation.Aggregation.group("classificationCode").count().as("count"),
                    org.springframework.data.mongodb.core.aggregation.Aggregation.sort(org.springframework.data.domain.Sort.Direction.ASC, "_id")
            );
            mongoTemplate.aggregate(codePipeline, "documents", org.bson.Document.class)
                    .getMappedResults().forEach(d -> byCode.put(d.getString("_id"), d.get("count", Number.class).longValue()));
        } catch (Exception e) { log.warn("Classification code aggregation failed: {}", e.getMessage()); }

        return ResponseEntity.ok(Map.of(
                "categories", byCat,
                "sensitivities", bySensitivity,
                "statuses", byStatus,
                "classificationCodes", byCode,
                "totalFiltered", totalFiltered,
                "metadataSchemas", schemas
        ));
    }

    // ── Taxonomy tree with document counts ─────────────────────

    @GetMapping("/taxonomy-tree")
    public ResponseEntity<List<Map<String, Object>>> taxonomyTree(@AuthenticationPrincipal UserDetails user) {
        List<ClassificationCategory> all = governanceService.getFullTaxonomy();

        // Aggregate document counts by classificationCode
        Map<String, Long> codeCounts = new java.util.LinkedHashMap<>();
        try {
            var pipeline = org.springframework.data.mongodb.core.aggregation.Aggregation.newAggregation(
                    org.springframework.data.mongodb.core.aggregation.Aggregation.match(
                            Criteria.where("status").ne("DISPOSED").and("classificationCode").ne(null)),
                    org.springframework.data.mongodb.core.aggregation.Aggregation.group("classificationCode").count().as("count")
            );
            mongoTemplate.aggregate(pipeline, "documents", org.bson.Document.class)
                    .getMappedResults().forEach(d -> codeCounts.put(d.getString("_id"), d.get("count", Number.class).longValue()));
        } catch (Exception e) { log.warn("Taxonomy tree aggregation failed: {}", e.getMessage()); }

        // Build lookup maps
        Map<String, List<ClassificationCategory>> byParent = new java.util.LinkedHashMap<>();
        Map<String, ClassificationCategory> byId = new java.util.LinkedHashMap<>();
        for (ClassificationCategory cat : all) {
            byId.put(cat.getId(), cat);
            byParent.computeIfAbsent(cat.getParentId() != null ? cat.getParentId() : "root", k -> new ArrayList<>()).add(cat);
        }

        // Build tree from roots
        List<Map<String, Object>> roots = new ArrayList<>();
        for (ClassificationCategory cat : byParent.getOrDefault("root", List.of())) {
            roots.add(buildTreeNode(cat, byParent, codeCounts));
        }
        return ResponseEntity.ok(roots);
    }

    private Map<String, Object> buildTreeNode(ClassificationCategory cat,
                                               Map<String, List<ClassificationCategory>> byParent,
                                               Map<String, Long> codeCounts) {
        List<Map<String, Object>> children = new ArrayList<>();
        long childTotal = 0;
        for (ClassificationCategory child : byParent.getOrDefault(cat.getId(), List.of())) {
            Map<String, Object> childNode = buildTreeNode(child, byParent, codeCounts);
            childTotal += ((Number) childNode.get("documentCount")).longValue();
            children.add(childNode);
        }
        long ownCount = codeCounts.getOrDefault(cat.getClassificationCode(), 0L);
        Map<String, Object> node = new java.util.LinkedHashMap<>();
        node.put("id", cat.getId());
        node.put("name", cat.getName());
        node.put("classificationCode", cat.getClassificationCode());
        node.put("level", cat.getLevel() != null ? cat.getLevel().name() : null);
        node.put("documentCount", ownCount + childTotal);
        node.put("ownDocumentCount", ownCount);
        node.put("children", children);
        return node;
    }

    record SearchRequest(
            String q,
            String status,
            String sensitivity,
            String categoryId,
            String categoryName,
            String classificationCode,
            String classificationCodePrefix,
            String classificationLevel,
            String mimeType,
            String uploadedBy,
            String createdAfter,
            String createdBefore,
            Map<String, String> metadata
    ) {}
}
