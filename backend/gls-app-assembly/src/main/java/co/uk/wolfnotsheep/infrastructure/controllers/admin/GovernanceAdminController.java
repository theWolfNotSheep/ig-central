package co.uk.wolfnotsheep.infrastructure.controllers.admin;

import co.uk.wolfnotsheep.document.models.DocumentModel;
import co.uk.wolfnotsheep.document.repositories.DocumentRepository;
import co.uk.wolfnotsheep.governance.models.*;
import co.uk.wolfnotsheep.governance.models.PiiTypeDefinition.ApprovalStatus;
import co.uk.wolfnotsheep.governance.repositories.*;
import co.uk.wolfnotsheep.governance.services.GovernanceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.*;

/**
 * Admin CRUD endpoints for managing the governance framework.
 * These endpoints require ADMIN role.
 */
@RestController
@RequestMapping("/api/admin/governance")
public class GovernanceAdminController {

    private static final Logger log = LoggerFactory.getLogger(GovernanceAdminController.class);

    private final GovernanceService governanceService;
    private final GovernancePolicyRepository policyRepository;
    private final ClassificationCategoryRepository categoryRepository;
    private final RetentionScheduleRepository retentionRepository;
    private final StorageTierRepository storageTierRepository;
    private final SensitivityDefinitionRepository sensitivityRepository;
    private final PiiTypeDefinitionRepository piiTypeRepository;
    private final MetadataSchemaRepository metadataSchemaRepository;
    private final DocumentRepository documentRepository;
    private final MongoTemplate mongoTemplate;
    private final co.uk.wolfnotsheep.platform.config.services.AppConfigService configService;
    private final co.uk.wolfnotsheep.document.repositories.AiUsageLogRepository aiUsageLogRepo;

    @Value("${spring.ai.anthropic.api-key:}")
    private String anthropicApiKey;

    public GovernanceAdminController(GovernanceService governanceService,
                                     GovernancePolicyRepository policyRepository,
                                     ClassificationCategoryRepository categoryRepository,
                                     RetentionScheduleRepository retentionRepository,
                                     StorageTierRepository storageTierRepository,
                                     SensitivityDefinitionRepository sensitivityRepository,
                                     PiiTypeDefinitionRepository piiTypeRepository,
                                     MetadataSchemaRepository metadataSchemaRepository,
                                     DocumentRepository documentRepository,
                                     MongoTemplate mongoTemplate,
                                     co.uk.wolfnotsheep.platform.config.services.AppConfigService configService,
                                     co.uk.wolfnotsheep.document.repositories.AiUsageLogRepository aiUsageLogRepo) {
        this.governanceService = governanceService;
        this.policyRepository = policyRepository;
        this.categoryRepository = categoryRepository;
        this.retentionRepository = retentionRepository;
        this.storageTierRepository = storageTierRepository;
        this.sensitivityRepository = sensitivityRepository;
        this.piiTypeRepository = piiTypeRepository;
        this.metadataSchemaRepository = metadataSchemaRepository;
        this.documentRepository = documentRepository;
        this.mongoTemplate = mongoTemplate;
        this.configService = configService;
        this.aiUsageLogRepo = aiUsageLogRepo;
    }

    // ── Sensitivity Definitions ─────────────────────────

    @GetMapping("/sensitivities")
    public ResponseEntity<List<SensitivityDefinition>> listSensitivities() {
        return ResponseEntity.ok(sensitivityRepository.findByActiveTrueOrderByLevelAsc());
    }

    @GetMapping("/sensitivities/all")
    public ResponseEntity<List<SensitivityDefinition>> listAllSensitivities() {
        return ResponseEntity.ok(sensitivityRepository.findAll());
    }

    @PostMapping("/sensitivities")
    public ResponseEntity<SensitivityDefinition> createSensitivity(@RequestBody SensitivityDefinition def) {
        def.setActive(true);
        return ResponseEntity.ok(sensitivityRepository.save(def));
    }

    @PutMapping("/sensitivities/{id}")
    public ResponseEntity<SensitivityDefinition> updateSensitivity(
            @PathVariable String id, @RequestBody SensitivityDefinition updates) {
        return sensitivityRepository.findById(id)
                .map(existing -> {
                    existing.setDisplayName(updates.getDisplayName());
                    existing.setDescription(updates.getDescription());
                    existing.setLevel(updates.getLevel());
                    existing.setColour(updates.getColour());
                    existing.setGuidelines(updates.getGuidelines());
                    existing.setExamples(updates.getExamples());
                    existing.setActive(updates.isActive());
                    return ResponseEntity.ok(sensitivityRepository.save(existing));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/sensitivities/{id}")
    public ResponseEntity<Void> deactivateSensitivity(@PathVariable String id) {
        return sensitivityRepository.findById(id)
                .map(def -> {
                    def.setActive(false);
                    sensitivityRepository.save(def);
                    return ResponseEntity.ok().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ── Policies ─────────────────────────────────────────

    @GetMapping("/policies")
    public ResponseEntity<List<GovernancePolicy>> listPolicies() {
        return ResponseEntity.ok(governanceService.getActivePolicies());
    }

    @PostMapping("/policies")
    public ResponseEntity<GovernancePolicy> createPolicy(@RequestBody GovernancePolicy policy) {
        policy.setCreatedAt(Instant.now());
        policy.setUpdatedAt(Instant.now());
        policy.setVersion(1);
        policy.setActive(true);
        return ResponseEntity.ok(policyRepository.save(policy));
    }

    @PutMapping("/policies/{id}")
    public ResponseEntity<GovernancePolicy> updatePolicy(
            @PathVariable String id, @RequestBody GovernancePolicy updates) {
        return policyRepository.findById(id)
                .map(existing -> {
                    existing.setName(updates.getName());
                    existing.setDescription(updates.getDescription());
                    existing.setRules(updates.getRules());
                    existing.setApplicableCategoryIds(updates.getApplicableCategoryIds());
                    existing.setApplicableSensitivities(updates.getApplicableSensitivities());
                    existing.setEnforcementActions(updates.getEnforcementActions());
                    existing.setEffectiveFrom(updates.getEffectiveFrom());
                    existing.setEffectiveUntil(updates.getEffectiveUntil());
                    existing.setVersion(existing.getVersion() + 1);
                    existing.setUpdatedAt(Instant.now());
                    return ResponseEntity.ok(policyRepository.save(existing));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/policies/{id}")
    public ResponseEntity<Void> deactivatePolicy(@PathVariable String id) {
        return policyRepository.findById(id)
                .map(policy -> {
                    policy.setActive(false);
                    policy.setUpdatedAt(Instant.now());
                    policyRepository.save(policy);
                    return ResponseEntity.ok().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ── Classification Taxonomy ──────────────────────────

    @GetMapping("/taxonomy")
    public ResponseEntity<List<ClassificationCategory>> listCategories() {
        return ResponseEntity.ok(governanceService.getFullTaxonomy());
    }

    @GetMapping("/taxonomy/tree")
    public ResponseEntity<String> getTaxonomyTree() {
        return ResponseEntity.ok(governanceService.getTaxonomyAsText());
    }

    @PostMapping("/taxonomy")
    public ResponseEntity<ClassificationCategory> createCategory(@RequestBody ClassificationCategory category) {
        category.setActive(true);
        return ResponseEntity.ok(categoryRepository.save(category));
    }

    @PutMapping("/taxonomy/{id}")
    public ResponseEntity<ClassificationCategory> updateCategory(
            @PathVariable String id, @RequestBody ClassificationCategory updates) {
        return categoryRepository.findById(id)
                .map(existing -> {
                    existing.setName(updates.getName());
                    existing.setDescription(updates.getDescription());
                    existing.setParentId(updates.getParentId());
                    existing.setKeywords(updates.getKeywords());
                    existing.setDefaultSensitivity(updates.getDefaultSensitivity());
                    existing.setRetentionScheduleId(updates.getRetentionScheduleId());
                    return ResponseEntity.ok(categoryRepository.save(existing));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/taxonomy/{id}")
    public ResponseEntity<Void> deactivateCategory(@PathVariable String id) {
        return categoryRepository.findById(id)
                .map(cat -> {
                    cat.setActive(false);
                    categoryRepository.save(cat);
                    return ResponseEntity.ok().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ── Retention Schedules ──────────────────────────────

    @GetMapping("/retention")
    public ResponseEntity<List<RetentionSchedule>> listRetentionSchedules() {
        return ResponseEntity.ok(governanceService.getAllRetentionSchedules());
    }

    @PostMapping("/retention")
    public ResponseEntity<RetentionSchedule> createRetentionSchedule(@RequestBody RetentionSchedule schedule) {
        return ResponseEntity.ok(retentionRepository.save(schedule));
    }

    @PutMapping("/retention/{id}")
    public ResponseEntity<RetentionSchedule> updateRetentionSchedule(
            @PathVariable String id, @RequestBody RetentionSchedule updates) {
        return retentionRepository.findById(id)
                .map(existing -> {
                    existing.setName(updates.getName());
                    existing.setDescription(updates.getDescription());
                    existing.setRetentionDays(updates.getRetentionDays());
                    existing.setDispositionAction(updates.getDispositionAction());
                    existing.setLegalHoldOverride(updates.isLegalHoldOverride());
                    existing.setRegulatoryBasis(updates.getRegulatoryBasis());
                    return ResponseEntity.ok(retentionRepository.save(existing));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ── Storage Tiers ────────────────────────────────────

    @GetMapping("/storage-tiers")
    public ResponseEntity<List<StorageTier>> listStorageTiers() {
        return ResponseEntity.ok(governanceService.getAllStorageTiers());
    }

    @PostMapping("/storage-tiers")
    public ResponseEntity<StorageTier> createStorageTier(@RequestBody StorageTier tier) {
        return ResponseEntity.ok(storageTierRepository.save(tier));
    }

    @PutMapping("/storage-tiers/{id}")
    public ResponseEntity<StorageTier> updateStorageTier(
            @PathVariable String id, @RequestBody StorageTier updates) {
        return storageTierRepository.findById(id)
                .map(existing -> {
                    existing.setName(updates.getName());
                    existing.setDescription(updates.getDescription());
                    existing.setEncryptionType(updates.getEncryptionType());
                    existing.setImmutable(updates.isImmutable());
                    existing.setGeographicallyRestricted(updates.isGeographicallyRestricted());
                    existing.setRegion(updates.getRegion());
                    existing.setAllowedSensitivities(updates.getAllowedSensitivities());
                    existing.setMaxFileSizeBytes(updates.getMaxFileSizeBytes());
                    existing.setCostPerGbMonth(updates.getCostPerGbMonth());
                    return ResponseEntity.ok(storageTierRepository.save(existing));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ── PII Type Definitions ──────────────────────────

    @GetMapping("/pii-types")
    public ResponseEntity<List<PiiTypeDefinition>> listPiiTypes() {
        return ResponseEntity.ok(
                piiTypeRepository.findByActiveTrueAndApprovalStatusOrderByCategoryAscDisplayNameAsc(
                        ApprovalStatus.APPROVED));
    }

    @GetMapping("/pii-types/all")
    public ResponseEntity<List<PiiTypeDefinition>> listAllPiiTypes() {
        return ResponseEntity.ok(piiTypeRepository.findAll());
    }

    @GetMapping("/pii-types/pending")
    public ResponseEntity<List<PiiTypeDefinition>> listPendingPiiTypes() {
        return ResponseEntity.ok(
                piiTypeRepository.findByApprovalStatusOrderBySubmittedAtDesc(ApprovalStatus.PENDING));
    }

    @PostMapping("/pii-types")
    public ResponseEntity<PiiTypeDefinition> createPiiType(@RequestBody PiiTypeDefinition def) {
        def.setActive(true);
        def.setApprovalStatus(ApprovalStatus.APPROVED);
        return ResponseEntity.ok(piiTypeRepository.save(def));
    }

    @PutMapping("/pii-types/{id}")
    public ResponseEntity<PiiTypeDefinition> updatePiiType(
            @PathVariable String id, @RequestBody PiiTypeDefinition updates) {
        return piiTypeRepository.findById(id)
                .map(existing -> {
                    existing.setDisplayName(updates.getDisplayName());
                    existing.setDescription(updates.getDescription());
                    existing.setCategory(updates.getCategory());
                    existing.setExamples(updates.getExamples());
                    existing.setActive(updates.isActive());
                    return ResponseEntity.ok(piiTypeRepository.save(existing));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/pii-types/{id}")
    public ResponseEntity<Void> deactivatePiiType(@PathVariable String id) {
        return piiTypeRepository.findById(id)
                .map(def -> {
                    def.setActive(false);
                    piiTypeRepository.save(def);
                    return ResponseEntity.ok().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/pii-types/{id}/approve")
    public ResponseEntity<PiiTypeDefinition> approvePiiType(
            @PathVariable String id, Authentication auth) {
        return piiTypeRepository.findById(id)
                .map(def -> {
                    def.setApprovalStatus(ApprovalStatus.APPROVED);
                    def.setActive(true);
                    def.setReviewedBy(auth != null ? auth.getName() : "ADMIN");
                    def.setReviewedAt(Instant.now());
                    return ResponseEntity.ok(piiTypeRepository.save(def));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/pii-types/{id}/reject")
    public ResponseEntity<PiiTypeDefinition> rejectPiiType(
            @PathVariable String id, @RequestBody Map<String, String> body, Authentication auth) {
        return piiTypeRepository.findById(id)
                .map(def -> {
                    def.setApprovalStatus(ApprovalStatus.REJECTED);
                    def.setActive(false);
                    def.setReviewedBy(auth != null ? auth.getName() : "ADMIN");
                    def.setReviewedAt(Instant.now());
                    def.setRejectionReason(body.getOrDefault("reason", ""));
                    return ResponseEntity.ok(piiTypeRepository.save(def));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ── Metadata Schemas ──────────────────────────────

    @GetMapping("/metadata-schemas")
    public ResponseEntity<List<MetadataSchema>> listMetadataSchemas() {
        return ResponseEntity.ok(metadataSchemaRepository.findByActiveTrueOrderByNameAsc());
    }

    @GetMapping("/metadata-schemas/all")
    public ResponseEntity<List<MetadataSchema>> listAllMetadataSchemas() {
        return ResponseEntity.ok(metadataSchemaRepository.findAll());
    }

    @PostMapping("/metadata-schemas")
    public ResponseEntity<MetadataSchema> createMetadataSchema(@RequestBody MetadataSchema schema) {
        schema.setActive(true);
        schema.setCreatedAt(Instant.now());
        schema.setUpdatedAt(Instant.now());
        return ResponseEntity.ok(metadataSchemaRepository.save(schema));
    }

    @PutMapping("/metadata-schemas/{id}")
    public ResponseEntity<MetadataSchema> updateMetadataSchema(
            @PathVariable String id, @RequestBody MetadataSchema updates) {
        return metadataSchemaRepository.findById(id)
                .map(existing -> {
                    existing.setName(updates.getName());
                    existing.setDescription(updates.getDescription());
                    existing.setFields(updates.getFields());
                    if (updates.getLinkedMimeTypes() != null) existing.setLinkedMimeTypes(updates.getLinkedMimeTypes());
                    existing.setActive(updates.isActive());
                    existing.setUpdatedAt(Instant.now());
                    return ResponseEntity.ok(metadataSchemaRepository.save(existing));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Add a field to a metadata schema dynamically.
     * Used when a user says "keep this field for all documents of this type".
     */
    @PostMapping("/metadata-schemas/{id}/fields")
    public ResponseEntity<MetadataSchema> addFieldToSchema(
            @PathVariable String id, @RequestBody MetadataSchema.MetadataField field) {
        return metadataSchemaRepository.findById(id)
                .map(schema -> {
                    var fields = new java.util.ArrayList<>(schema.getFields() != null ? schema.getFields() : List.of());
                    // Don't add duplicate
                    if (fields.stream().noneMatch(f -> f.fieldName().equals(field.fieldName()))) {
                        fields.add(field);
                        schema.setFields(fields);
                        schema.setUpdatedAt(Instant.now());
                    }
                    return ResponseEntity.ok(metadataSchemaRepository.save(schema));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Remove a field from a metadata schema.
     */
    @DeleteMapping("/metadata-schemas/{id}/fields/{fieldName}")
    public ResponseEntity<MetadataSchema> removeFieldFromSchema(
            @PathVariable String id, @PathVariable String fieldName) {
        return metadataSchemaRepository.findById(id)
                .map(schema -> {
                    if (schema.getFields() != null) {
                        schema.setFields(schema.getFields().stream()
                                .filter(f -> !f.fieldName().equals(fieldName)).toList());
                        schema.setUpdatedAt(Instant.now());
                    }
                    return ResponseEntity.ok(metadataSchemaRepository.save(schema));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/metadata-schemas/{id}")
    public ResponseEntity<Void> deactivateMetadataSchema(@PathVariable String id) {
        return metadataSchemaRepository.findById(id)
                .map(schema -> {
                    schema.setActive(false);
                    schema.setUpdatedAt(Instant.now());
                    metadataSchemaRepository.save(schema);
                    return ResponseEntity.ok().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ── Schema Coverage & Quality ─────────────────────────

    /**
     * Returns all active taxonomy categories with their schema status,
     * document counts, and metadata extraction coverage.
     */
    @GetMapping("/metadata-schemas/coverage")
    public ResponseEntity<List<Map<String, Object>>> getSchemaCoverage() {
        List<ClassificationCategory> categories = categoryRepository.findByActiveTrue();
        List<MetadataSchema> activeSchemas = metadataSchemaRepository.findByActiveTrueOrderByNameAsc();

        // Build a lookup: schemaId -> schema
        Map<String, MetadataSchema> schemaById = new HashMap<>();
        for (MetadataSchema schema : activeSchemas) {
            schemaById.put(schema.getId(), schema);
        }

        // Build category full-name lookup (resolve parent names for display)
        Map<String, ClassificationCategory> categoryById = new HashMap<>();
        for (ClassificationCategory cat : categories) {
            categoryById.put(cat.getId(), cat);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (ClassificationCategory cat : categories) {
            String fullName = buildCategoryPath(cat, categoryById);
            MetadataSchema schema = cat.getMetadataSchemaId() != null
                    ? schemaById.get(cat.getMetadataSchemaId()) : null;

            // Count documents in this category
            long documentCount = mongoTemplate.count(
                    Query.query(Criteria.where("categoryId").is(cat.getId())),
                    DocumentModel.class);

            // Count documents with non-null extractedMetadata
            long documentsWithMetadata = mongoTemplate.count(
                    Query.query(Criteria.where("categoryId").is(cat.getId())
                            .and("extractedMetadata").ne(null)),
                    DocumentModel.class);

            // Count documents with at least one "NOT_FOUND" in extractedMetadata values
            long documentsWithMissing = 0;
            if (schema != null && documentsWithMetadata > 0) {
                documentsWithMissing = mongoTemplate.count(
                        Query.query(Criteria.where("categoryId").is(cat.getId())
                                .and("extractedMetadata").ne(null)
                                .orOperator(
                                        Criteria.where("extractedMetadata").regex("NOT_FOUND")
                                )),
                        DocumentModel.class);
            }

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("categoryId", cat.getId());
            entry.put("categoryName", fullName);
            entry.put("hasSchema", schema != null);
            entry.put("schemaName", schema != null ? schema.getName() : null);
            entry.put("fieldCount", schema != null && schema.getFields() != null ? schema.getFields().size() : 0);
            entry.put("documentCount", documentCount);
            entry.put("documentsWithMetadata", documentsWithMetadata);
            entry.put("documentsWithMissing", documentsWithMissing);
            result.add(entry);
        }

        return ResponseEntity.ok(result);
    }

    /**
     * Returns metadata quality assessment for a specific document:
     * compares schema fields against the document's extractedMetadata.
     */
    @GetMapping("/metadata-schemas/quality/{documentId}")
    public ResponseEntity<Map<String, Object>> getMetadataQuality(@PathVariable String documentId) {
        Optional<DocumentModel> docOpt = documentRepository.findById(documentId);
        if (docOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        DocumentModel doc = docOpt.get();
        String categoryId = doc.getCategoryId();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("documentId", doc.getId());
        result.put("categoryName", doc.getCategoryName());

        if (categoryId == null) {
            result.put("hasSchema", false);
            result.put("schemaName", null);
            result.put("fields", List.of());
            result.put("completeness", 0.0);
            return ResponseEntity.ok(result);
        }

        // Find schema for this category
        List<MetadataSchema> schemas = governanceService.getSchemasForCategory(categoryId);

        if (schemas.isEmpty()) {
            result.put("hasSchema", false);
            result.put("schemaName", null);
            result.put("fields", List.of());
            result.put("completeness", 0.0);
            return ResponseEntity.ok(result);
        }

        MetadataSchema schema = schemas.getFirst();
        Map<String, String> extractedMetadata = doc.getExtractedMetadata();
        if (extractedMetadata == null) extractedMetadata = Map.of();

        List<Map<String, Object>> fieldResults = new ArrayList<>();
        int foundCount = 0;
        int totalFields = schema.getFields() != null ? schema.getFields().size() : 0;

        if (schema.getFields() != null) {
            for (MetadataSchema.MetadataField field : schema.getFields()) {
                Map<String, Object> fieldEntry = new LinkedHashMap<>();
                fieldEntry.put("fieldName", field.fieldName());
                fieldEntry.put("dataType", field.dataType().name());
                fieldEntry.put("required", field.required());

                String value = extractedMetadata.get(field.fieldName());
                fieldEntry.put("value", value);

                if (value != null && !"NOT_FOUND".equals(value)) {
                    fieldEntry.put("status", "FOUND");
                    foundCount++;
                } else if ("NOT_FOUND".equals(value)) {
                    fieldEntry.put("status", "MISSING");
                } else {
                    fieldEntry.put("status", "NOT_EXTRACTED");
                }

                fieldResults.add(fieldEntry);
            }
        }

        result.put("hasSchema", true);
        result.put("schemaName", schema.getName());
        result.put("fields", fieldResults);
        result.put("completeness", totalFields > 0 ? Math.round((double) foundCount / totalFields * 100.0) / 100.0 : 0.0);

        return ResponseEntity.ok(result);
    }

    /**
     * Uses the LLM to suggest metadata schema fields for a document
     * based on its extracted text content.
     */
    @PostMapping("/metadata-schemas/suggest")
    public ResponseEntity<Map<String, Object>> suggestSchemaFields(@RequestBody Map<String, String> body, org.springframework.security.core.Authentication auth) {
        String documentId = body.get("documentId");
        if (documentId == null || documentId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "documentId is required"));
        }

        Optional<DocumentModel> docOpt = documentRepository.findById(documentId);
        if (docOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        DocumentModel doc = docOpt.get();
        String extractedText = doc.getExtractedText();
        if (extractedText == null || extractedText.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Document has no extracted text"));
        }

        // Truncate text to avoid exceeding token limits
        String textSnippet = extractedText.length() > 6000
                ? extractedText.substring(0, 6000) + "\n... [truncated]"
                : extractedText;

        String prompt = """
                Analyse this document and suggest structured metadata fields that should be extracted. \
                Return a JSON array of objects with fieldName (snake_case), dataType (one of: TEXT, KEYWORD, DATE, NUMBER, CURRENCY, BOOLEAN), \
                required (boolean), description (string), and examples (array of example values from the document).

                Only suggest fields that are clearly present or expected in this type of document. \
                Be specific with field names — use descriptive snake_case names like "invoice_number" not generic ones like "field1".

                Document file: %s
                Document category: %s

                --- DOCUMENT TEXT ---
                %s
                --- END ---

                Return ONLY the JSON array, no other text.""".formatted(
                doc.getOriginalFileName(),
                doc.getCategoryName() != null ? doc.getCategoryName() : "uncategorised",
                textSnippet);

        try {
            long start = System.currentTimeMillis();
            String suggestion = callLlm(prompt);
            long duration = System.currentTimeMillis() - start;

            // Log AI usage
            var usageLog = new co.uk.wolfnotsheep.document.models.AiUsageLog();
            usageLog.setUsageType("SUGGEST_SCHEMA");
            usageLog.setTriggeredBy(auth != null ? auth.getName() : "SYSTEM");
            usageLog.setDocumentId(documentId);
            usageLog.setDocumentName(doc.getOriginalFileName());
            usageLog.setProvider(configService.getValue("llm.provider", "anthropic"));
            usageLog.setModel(configService.getValue("llm." + configService.getValue("llm.provider", "anthropic") + ".model", "unknown"));
            usageLog.setUserPrompt(prompt.length() > 2000 ? prompt.substring(0, 2000) + "..." : prompt);
            usageLog.setResponse(suggestion.length() > 5000 ? suggestion.substring(0, 5000) + "..." : suggestion);
            usageLog.setDurationMs(duration);
            usageLog.setStatus("SUCCESS");
            try { aiUsageLogRepo.save(usageLog); } catch (Exception logErr) { log.error("Failed to save AI usage log: {}", logErr.getMessage()); }

            return ResponseEntity.ok(Map.of(
                    "documentId", documentId,
                    "fileName", doc.getOriginalFileName() != null ? doc.getOriginalFileName() : "",
                    "categoryName", doc.getCategoryName() != null ? doc.getCategoryName() : "",
                    "suggestedFields", suggestion
            ));
        } catch (Exception e) {
            log.error("Schema suggestion LLM call failed: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "LLM call failed: " + e.getMessage()
            ));
        }
    }

    /**
     * Tests a metadata schema against a specific document by calling the LLM
     * to extract fields. Returns extracted values for preview without saving.
     */
    @PostMapping("/metadata-schemas/test")
    public ResponseEntity<Map<String, Object>> testSchemaExtraction(@RequestBody Map<String, String> body) {
        String documentId = body.get("documentId");
        String schemaId = body.get("schemaId");

        if (documentId == null || documentId.isBlank() || schemaId == null || schemaId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "documentId and schemaId are required"));
        }

        Optional<DocumentModel> docOpt = documentRepository.findById(documentId);
        if (docOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Optional<MetadataSchema> schemaOpt = metadataSchemaRepository.findById(schemaId);
        if (schemaOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Schema not found: " + schemaId));
        }

        DocumentModel doc = docOpt.get();
        MetadataSchema schema = schemaOpt.get();

        String extractedText = doc.getExtractedText();
        if (extractedText == null || extractedText.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Document has no extracted text"));
        }

        // Truncate text to avoid exceeding token limits
        String textSnippet = extractedText.length() > 6000
                ? extractedText.substring(0, 6000) + "\n... [truncated]"
                : extractedText;

        // Build field list for the prompt
        StringBuilder fieldList = new StringBuilder();
        if (schema.getFields() != null) {
            for (MetadataSchema.MetadataField field : schema.getFields()) {
                fieldList.append("- ").append(field.fieldName())
                        .append(" (").append(field.dataType().name()).append(")")
                        .append(field.required() ? " [REQUIRED]" : " [optional]");
                if (field.description() != null) fieldList.append(": ").append(field.description());
                if (field.examples() != null && !field.examples().isEmpty()) {
                    fieldList.append(" examples: ").append(String.join(", ", field.examples()));
                }
                fieldList.append("\n");
            }
        }

        String prompt = """
                Extract ONLY these fields from the document text. Return a JSON object where each key is the field name \
                and the value is the extracted value as a string. If a field value cannot be determined, use "NOT_FOUND".

                Schema: %s
                Fields to extract:
                %s
                Document file: %s

                --- DOCUMENT TEXT ---
                %s
                --- END ---

                Return ONLY the JSON object, no other text.""".formatted(
                schema.getName(),
                fieldList.toString(),
                doc.getOriginalFileName() != null ? doc.getOriginalFileName() : "",
                textSnippet);

        try {
            String extracted = callLlm(prompt);
            return ResponseEntity.ok(Map.of(
                    "documentId", documentId,
                    "schemaId", schemaId,
                    "schemaName", schema.getName(),
                    "fileName", doc.getOriginalFileName() != null ? doc.getOriginalFileName() : "",
                    "extractedFields", extracted
            ));
        } catch (Exception e) {
            log.error("Schema test LLM call failed: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "LLM call failed: " + e.getMessage()
            ));
        }
    }

    // ── LLM Helper ────────────────────────────────────────

    private String callLlm(String prompt) throws Exception {
        String escapedPrompt = prompt.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");

        // Check which provider is configured
        String provider = configService.getValue("llm.provider", "anthropic");
        if ("ollama".equalsIgnoreCase(provider)) {
            return callOllama(escapedPrompt);
        }

        // Anthropic
        String apiKey = configService.getValue("llm.anthropic.api_key", anthropicApiKey);
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("No LLM configured. Set Anthropic API key or switch to Ollama in Settings.");
        }
        String model = configService.getValue("llm.anthropic.model", "claude-sonnet-4-20250514");

        String requestBody = """
                {"model":"%s","max_tokens":4096,"messages":[{"role":"user","content":"%s"}]}
                """.formatted(model, escapedPrompt);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.anthropic.com/v1/messages"))
                .header("Content-Type", "application/json")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("LLM API error: " + response.statusCode());
        }

        return extractTextFromAnthropicResponse(response.body());
    }

    private String callOllama(String escapedPrompt) throws Exception {
        String baseUrl = configService.getValue("llm.ollama.base_url", "http://localhost:11434");
        String model = configService.getValue("llm.ollama.model", "qwen2.5:32b");

        String requestBody = """
                {"model":"%s","prompt":"%s","stream":false}
                """.formatted(model, escapedPrompt);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/generate"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Ollama API error: " + response.statusCode());
        }

        // Ollama response: {"response":"...","done":true}
        String body = response.body();
        int start = body.indexOf("\"response\":\"") + 12;
        int end = body.indexOf("\",\"done\"");
        if (start < 12 || end < 0) {
            // Try to find response field more flexibly
            start = body.indexOf("\"response\":\"") + 12;
            end = body.lastIndexOf("\"");
        }
        if (start >= 12 && end > start) {
            return body.substring(start, end).replace("\\n", "\n").replace("\\\"", "\"").replace("\\t", "\t");
        }
        return body;
    }

    private String extractTextFromAnthropicResponse(String responseBody) {
        int textStart = responseBody.indexOf("\"text\":\"") + 8;
        int textEnd = responseBody.indexOf("\"", textStart);
        if (textStart < 8 || textEnd < 0) {
            int contentStart = responseBody.indexOf("\"text\":");
            if (contentStart >= 0) {
                return responseBody.substring(contentStart + 8, responseBody.indexOf("\"", contentStart + 8))
                        .replace("\\n", "\n").replace("\\\"", "\"");
            }
            return responseBody;
        }
        return responseBody.substring(textStart, textEnd).replace("\\n", "\n").replace("\\\"", "\"");
    }

    // ── Helpers ───────────────────────────────────────────

    private String buildCategoryPath(ClassificationCategory category,
                                     Map<String, ClassificationCategory> categoryById) {
        List<String> parts = new ArrayList<>();
        ClassificationCategory current = category;
        while (current != null) {
            parts.addFirst(current.getName());
            current = current.getParentId() != null ? categoryById.get(current.getParentId()) : null;
        }
        return String.join(" > ", parts);
    }
}
