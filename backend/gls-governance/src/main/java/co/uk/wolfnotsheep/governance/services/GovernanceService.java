package co.uk.wolfnotsheep.governance.services;

import co.uk.wolfnotsheep.governance.models.*;
import co.uk.wolfnotsheep.governance.models.ClassificationCorrection.CorrectionType;
import co.uk.wolfnotsheep.governance.repositories.*;
import co.uk.wolfnotsheep.governance.repositories.PiiTypeDefinitionRepository;
import co.uk.wolfnotsheep.governance.repositories.SensitivityDefinitionRepository;
import co.uk.wolfnotsheep.governance.repositories.TraitDefinitionRepository;
import org.springframework.stereotype.Service;

import co.uk.wolfnotsheep.governance.models.ClassificationCategory.TaxonomyLevel;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Central service for governance operations. Used by both the MCP server
 * (to expose governance context to LLMs) and the main API (for CRUD).
 */
@Service
public class GovernanceService {

    private final GovernancePolicyRepository policyRepository;
    private final ClassificationCategoryRepository categoryRepository;
    private final RetentionScheduleRepository retentionRepository;
    private final StorageTierRepository storageTierRepository;
    private final DocumentClassificationResultRepository classificationResultRepository;
    private final ClassificationCorrectionRepository correctionRepository;
    private final MetadataSchemaRepository metadataSchemaRepository;
    private final SensitivityDefinitionRepository sensitivityRepository;
    private final TraitDefinitionRepository traitRepository;
    private final PiiTypeDefinitionRepository piiTypeRepository;

    public GovernanceService(GovernancePolicyRepository policyRepository,
                             ClassificationCategoryRepository categoryRepository,
                             RetentionScheduleRepository retentionRepository,
                             StorageTierRepository storageTierRepository,
                             DocumentClassificationResultRepository classificationResultRepository,
                             ClassificationCorrectionRepository correctionRepository,
                             MetadataSchemaRepository metadataSchemaRepository,
                             SensitivityDefinitionRepository sensitivityRepository,
                             TraitDefinitionRepository traitRepository,
                             PiiTypeDefinitionRepository piiTypeRepository) {
        this.policyRepository = policyRepository;
        this.categoryRepository = categoryRepository;
        this.retentionRepository = retentionRepository;
        this.storageTierRepository = storageTierRepository;
        this.classificationResultRepository = classificationResultRepository;
        this.correctionRepository = correctionRepository;
        this.metadataSchemaRepository = metadataSchemaRepository;
        this.sensitivityRepository = sensitivityRepository;
        this.traitRepository = traitRepository;
        this.piiTypeRepository = piiTypeRepository;
    }

    // ── Policies ─────────────────────────────────────────────

    public List<GovernancePolicy> getActivePolicies() {
        return policyRepository.findByActiveTrue();
    }

    public List<GovernancePolicy> getEffectivePolicies() {
        Instant now = Instant.now();
        return policyRepository.findByActiveTrueAndEffectiveFromBeforeAndEffectiveUntilAfter(now, now);
    }

    public List<GovernancePolicy> getPoliciesForCategory(String categoryId) {
        return policyRepository.findByApplicableCategoryIdsContaining(categoryId);
    }

    public List<GovernancePolicy> getPoliciesForSensitivity(SensitivityLabel label) {
        return policyRepository.findByApplicableSensitivitiesContaining(label);
    }

    // ── Classification Taxonomy ──────────────────────────────

    public List<ClassificationCategory> getFullTaxonomy() {
        return categoryRepository.findByActiveTrue();
    }

    public List<ClassificationCategory> getRootCategories() {
        return categoryRepository.findByParentIdIsNullAndActiveTrue();
    }

    public List<ClassificationCategory> getChildCategories(String parentId) {
        return categoryRepository.findByParentIdAndActiveTrue(parentId);
    }

    /**
     * Builds the taxonomy as a nested structure for LLM consumption.
     * Includes ISO 15489 classification codes and taxonomy levels.
     */
    public String getTaxonomyAsText() {
        List<ClassificationCategory> all = getFullTaxonomy();
        Map<String, List<ClassificationCategory>> byParent = all.stream()
                .collect(Collectors.groupingBy(
                        c -> c.getParentId() != null ? c.getParentId() : "ROOT"));

        StringBuilder sb = new StringBuilder();
        for (ClassificationCategory root : byParent.getOrDefault("ROOT", List.of())) {
            buildTaxonomyTree(sb, root, byParent, 0);
        }
        return sb.toString();
    }

    private void buildTaxonomyTree(StringBuilder sb, ClassificationCategory node,
                                   Map<String, List<ClassificationCategory>> byParent, int depth) {
        sb.append("  ".repeat(depth))
                .append("- ");
        if (node.getClassificationCode() != null) {
            sb.append("[").append(node.getClassificationCode()).append("] ");
        }
        sb.append(node.getName());
        if (node.getLevel() != null) {
            sb.append(" (").append(node.getLevel().name().toLowerCase()).append(")");
        }
        sb.append(": ").append(node.getDescription());
        sb.append(" [default sensitivity: ").append(node.getDefaultSensitivity()).append("]");
        if (node.isPersonalDataFlag()) {
            sb.append(" [PERSONAL DATA]");
        }
        if (node.getTypicalRecords() != null && !node.getTypicalRecords().isEmpty()) {
            sb.append("\n").append("  ".repeat(depth + 1))
                    .append("Typical records: ").append(String.join(", ", node.getTypicalRecords()));
        }
        if (node.getRetentionPeriodText() != null && !node.getRetentionPeriodText().isBlank()) {
            sb.append("\n").append("  ".repeat(depth + 1))
                    .append("Retention: ").append(node.getRetentionPeriodText());
            if (node.getLegalCitation() != null && !node.getLegalCitation().isBlank()) {
                sb.append(" (").append(node.getLegalCitation()).append(")");
            }
        }
        if (node.getScopeNotes() != null && !node.getScopeNotes().isBlank()) {
            sb.append("\n").append("  ".repeat(depth + 1))
                    .append("Scope: ").append(node.getScopeNotes());
        }
        sb.append("\n");

        List<ClassificationCategory> children = byParent.getOrDefault(node.getId(), List.of());
        children.stream()
                .sorted(Comparator.comparingInt(ClassificationCategory::getSortOrder))
                .forEach(child -> buildTaxonomyTree(sb, child, byParent, depth + 1));
    }

    /**
     * Resolve inherited governance properties for a category by walking up the taxonomy tree.
     * Returns the effective retention schedule ID, sensitivity, and disposal authority
     * by checking each ancestor until a non-null value is found.
     */
    public ResolvedGovernance resolveTaxonomyInheritance(String categoryId) {
        ClassificationCategory category = categoryRepository.findById(categoryId).orElse(null);
        if (category == null) return null;

        String retentionScheduleId = category.getRetentionScheduleId();
        SensitivityLabel sensitivity = category.getDefaultSensitivity();
        String legalCitation = category.getLegalCitation();
        ClassificationCategory.RetentionTrigger retentionTrigger = category.getRetentionTrigger();

        // Walk up the tree until all properties are resolved
        String parentId = category.getParentId();
        while (parentId != null && (retentionScheduleId == null || sensitivity == null
                || legalCitation == null || retentionTrigger == null)) {
            ClassificationCategory parent = categoryRepository.findById(parentId).orElse(null);
            if (parent == null) break;

            if (retentionScheduleId == null) retentionScheduleId = parent.getRetentionScheduleId();
            if (sensitivity == null) sensitivity = parent.getDefaultSensitivity();
            if (legalCitation == null) legalCitation = parent.getLegalCitation();
            if (retentionTrigger == null) retentionTrigger = parent.getRetentionTrigger();

            parentId = parent.getParentId();
        }

        return new ResolvedGovernance(retentionScheduleId, sensitivity, legalCitation, retentionTrigger);
    }

    public record ResolvedGovernance(
            String retentionScheduleId,
            SensitivityLabel sensitivity,
            String legalCitation,
            ClassificationCategory.RetentionTrigger retentionTrigger
    ) {}

    /**
     * Rebuild materialised paths for all taxonomy nodes.
     * Should be called after bulk imports or structural changes.
     */
    public void rebuildPaths() {
        List<ClassificationCategory> all = categoryRepository.findAll();
        Map<String, ClassificationCategory> byId = all.stream()
                .collect(Collectors.toMap(ClassificationCategory::getId, c -> c));

        for (ClassificationCategory node : all) {
            List<String> path = new ArrayList<>();
            buildPath(node, byId, path);
            Collections.reverse(path);
            node.setPath(path);
        }

        categoryRepository.saveAll(all);
    }

    private void buildPath(ClassificationCategory node, Map<String, ClassificationCategory> byId, List<String> path) {
        String code = node.getClassificationCode();
        if (code != null) {
            path.add(code);
        }
        if (node.getParentId() != null) {
            ClassificationCategory parent = byId.get(node.getParentId());
            if (parent != null) {
                buildPath(parent, byId, path);
            }
        }
    }

    // ── Retention ────────────────────────────────────────────

    public List<RetentionSchedule> getAllRetentionSchedules() {
        return retentionRepository.findAll();
    }

    public RetentionSchedule getRetentionSchedule(String id) {
        return retentionRepository.findById(id).orElse(null);
    }

    // ── Storage Tiers ────────────────────────────────────────

    public List<StorageTier> getAllStorageTiers() {
        return storageTierRepository.findAll();
    }

    public List<StorageTier> getStorageTiersForSensitivity(SensitivityLabel label) {
        return storageTierRepository.findByAllowedSensitivitiesContaining(label);
    }

    // ── Classification Results ───────────────────────────────

    public DocumentClassificationResult saveClassificationResult(DocumentClassificationResult result) {
        result.setClassifiedAt(Instant.now());
        return classificationResultRepository.save(result);
    }

    public List<DocumentClassificationResult> getClassificationHistory(String documentId) {
        return classificationResultRepository.findByDocumentIdOrderByClassifiedAtDesc(documentId);
    }

    public List<DocumentClassificationResult> getLowConfidenceResults(double threshold) {
        return classificationResultRepository.findByHumanReviewedFalseAndConfidenceLessThan(threshold);
    }

    // ── Classification Corrections ────────────────────────

    public ClassificationCorrection saveCorrection(ClassificationCorrection correction) {
        correction.setCorrectedAt(Instant.now());
        return correctionRepository.save(correction);
    }

    public List<ClassificationCorrection> getRecentCorrections(int limit) {
        return correctionRepository.findTop20ByOrderByCorrectedAtDesc();
    }

    public List<ClassificationCorrection> getCorrectionsByCategory(String categoryId) {
        return correctionRepository.findByOriginalCategoryIdOrderByCorrectedAtDesc(categoryId);
    }

    public List<ClassificationCorrection> getCorrectionsByDocumentId(String documentId) {
        return correctionRepository.findByDocumentId(documentId);
    }

    public List<ClassificationCorrection> getPiiCorrections() {
        return correctionRepository.findTop50ByCorrectionTypeOrderByCorrectedAtDesc(CorrectionType.PII_FLAGGED);
    }

    // ── Metadata Schemas ────────────────────────────────

    public List<MetadataSchema> getActiveMetadataSchemas() {
        return metadataSchemaRepository.findByActiveTrueOrderByNameAsc();
    }

    public List<MetadataSchema> getSchemasForCategory(String categoryId) {
        if (categoryId == null) return List.of();
        return categoryRepository.findById(categoryId)
                .filter(cat -> cat.getMetadataSchemaId() != null)
                .flatMap(cat -> metadataSchemaRepository.findById(cat.getMetadataSchemaId()))
                .filter(MetadataSchema::isActive)
                .map(List::of)
                .orElse(List.of());
    }

    /**
     * Build a text representation of metadata schemas for LLM consumption.
     * The LLM uses this to know which fields to extract for each category.
     */
    /**
     * Build text for a specific category's schema only.
     * Returns null if no schema matches.
     */
    public String getMetadataSchemaForCategory(String categoryId) {
        if (categoryId == null) return null;
        List<MetadataSchema> schemas = getSchemasForCategory(categoryId);
        if (schemas.isEmpty()) return null;
        return formatSchema(schemas.getFirst());
    }

    /** Format active sensitivity definitions as a text block for LLM prompts. */
    public String getSensitivityDefinitionsAsText() {
        List<SensitivityDefinition> defs = sensitivityRepository.findByActiveTrueOrderByLevelAsc();
        if (defs.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (SensitivityDefinition d : defs) {
            sb.append("- **").append(d.getKey()).append("** (level ").append(d.getLevel()).append(")");
            if (d.getDisplayName() != null) sb.append(" — ").append(d.getDisplayName());
            sb.append("\n");
            if (d.getDescription() != null && !d.getDescription().isBlank()) {
                sb.append("  ").append(d.getDescription()).append("\n");
            }
            if (d.getGuidelines() != null && !d.getGuidelines().isEmpty()) {
                sb.append("  Guidelines: ").append(String.join("; ", d.getGuidelines())).append("\n");
            }
            if (d.getExamples() != null && !d.getExamples().isEmpty()) {
                sb.append("  Examples: ").append(String.join(", ", d.getExamples())).append("\n");
            }
        }
        return sb.toString();
    }

    /** Format active trait definitions as a text block for LLM prompts. */
    public String getTraitDefinitionsAsText() {
        List<TraitDefinition> traits = traitRepository.findByActiveTrueOrderByDimensionAscDisplayNameAsc();
        if (traits.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        // Group by dimension
        java.util.Map<String, List<TraitDefinition>> byDimension = new java.util.LinkedHashMap<>();
        for (TraitDefinition t : traits) {
            byDimension.computeIfAbsent(t.getDimension() != null ? t.getDimension() : "OTHER",
                    k -> new java.util.ArrayList<>()).add(t);
        }
        for (var e : byDimension.entrySet()) {
            sb.append("### ").append(e.getKey()).append("\n");
            for (TraitDefinition t : e.getValue()) {
                sb.append("- **").append(t.getKey()).append("** — ").append(t.getDisplayName());
                if (t.getDescription() != null) sb.append(": ").append(t.getDescription());
                sb.append("\n");
                if (t.getDetectionHint() != null && !t.getDetectionHint().isBlank()) {
                    sb.append("  Hint: ").append(t.getDetectionHint()).append("\n");
                }
            }
        }
        return sb.toString();
    }

    /** Format active PII type definitions as a text block for LLM prompts. */
    public String getPiiTypeDefinitionsAsText() {
        List<PiiTypeDefinition> defs = piiTypeRepository.findByActiveTrueAndApprovalStatusOrderByCategoryAscDisplayNameAsc(
                PiiTypeDefinition.ApprovalStatus.APPROVED);
        if (defs.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (PiiTypeDefinition d : defs) {
            sb.append("- **").append(d.getKey()).append("**");
            if (d.getDisplayName() != null) sb.append(" — ").append(d.getDisplayName());
            sb.append("\n");
            if (d.getDescription() != null && !d.getDescription().isBlank()) {
                sb.append("  ").append(d.getDescription()).append("\n");
            }
        }
        return sb.toString();
    }

    public String getMetadataSchemasAsText() {
        List<MetadataSchema> schemas = getActiveMetadataSchemas();
        if (schemas.isEmpty()) return "No metadata extraction schemas configured.";

        StringBuilder sb = new StringBuilder();
        for (MetadataSchema schema : schemas) {
            sb.append(formatSchema(schema)).append("\n");
        }
        return sb.toString();
    }

    private String formatSchema(MetadataSchema schema) {
        StringBuilder sb = new StringBuilder();
        sb.append("### ").append(schema.getName());
        if (schema.getDescription() != null) {
            sb.append(" — ").append(schema.getDescription());
        }
        sb.append("\n");

        if (schema.getExtractionContext() != null && !schema.getExtractionContext().isBlank()) {
            sb.append("\n**Context:** ").append(schema.getExtractionContext()).append("\n\n");
        }

        List<ClassificationCategory> linkedCategories = categoryRepository.findByMetadataSchemaId(schema.getId());
        if (!linkedCategories.isEmpty()) {
            List<String> catNames = linkedCategories.stream()
                    .map(ClassificationCategory::getName)
                    .toList();
            sb.append("Applies to categories: ").append(String.join(", ", catNames)).append("\n");
        }

        sb.append("\nExtract ONLY these fields (nothing else):\n");
        if (schema.getFields() != null) {
            for (MetadataSchema.MetadataField field : schema.getFields()) {
                sb.append("  - **").append(field.fieldName()).append("** (")
                        .append(field.dataType().name().toLowerCase()).append(")");
                if (field.required()) sb.append(" [REQUIRED]");
                if (field.description() != null) sb.append(": ").append(field.description());
                if (field.extractionHint() != null && !field.extractionHint().isBlank()) {
                    sb.append("\n    → Hint: ").append(field.extractionHint());
                }
                if (field.examples() != null && !field.examples().isEmpty()) {
                    sb.append("\n    → Examples: ").append(String.join(", ", field.examples()));
                }
                sb.append("\n");
            }
        }

        return sb.toString();
    }

    public List<ClassificationCorrection> getPiiDismissals() {
        return correctionRepository.findTop50ByCorrectionTypeOrderByCorrectedAtDesc(CorrectionType.PII_DISMISSED);
    }

    /**
     * Build a text summary of recent corrections for LLM consumption.
     * This is the key feedback mechanism — the LLM reads past human corrections
     * to improve its accuracy on similar documents.
     */
    public String getCorrectionsSummaryForLlm(String categoryId, String mimeType) {
        StringBuilder sb = new StringBuilder();

        // Get corrections relevant to this category (bounded)
        List<ClassificationCorrection> byCat = categoryId != null
                ? correctionRepository.findTop50ByOriginalCategoryIdOrderByCorrectedAtDesc(categoryId)
                : List.of();

        // Get corrections for this mime type (bounded)
        List<ClassificationCorrection> byMime = mimeType != null
                ? correctionRepository.findTop50ByMimeTypeOrderByCorrectedAtDesc(mimeType)
                : List.of();

        // Get recent PII corrections (bounded)
        List<ClassificationCorrection> pii = correctionRepository
                .findTop50ByCorrectionTypeOrderByCorrectedAtDesc(CorrectionType.PII_FLAGGED);

        // Recent general corrections
        List<ClassificationCorrection> recent = correctionRepository.findTop20ByOrderByCorrectedAtDesc();

        if (byCat.isEmpty() && byMime.isEmpty() && pii.isEmpty() && recent.isEmpty()) {
            return "No prior human corrections recorded yet.";
        }

        if (!byCat.isEmpty()) {
            sb.append("## Corrections for this category\n");
            for (var c : byCat.stream().limit(10).toList()) {
                formatCorrection(sb, c);
            }
            sb.append("\n");
        }

        if (!byMime.isEmpty()) {
            sb.append("## Corrections for this document type (").append(mimeType).append(")\n");
            for (var c : byMime.stream().limit(5).toList()) {
                formatCorrection(sb, c);
            }
            sb.append("\n");
        }

        if (!pii.isEmpty()) {
            sb.append("## PII corrections — types the system missed\n");
            for (var c : pii.stream().limit(10).toList()) {
                if (c.getPiiCorrections() != null) {
                    for (var p : c.getPiiCorrections()) {
                        sb.append("- Type: ").append(p.type())
                                .append(", Context: ").append(p.context())
                                .append(", Description: ").append(p.description()).append("\n");
                    }
                }
            }
            sb.append("\n");
        }

        if (!recent.isEmpty() && byCat.isEmpty()) {
            sb.append("## Recent corrections (general)\n");
            for (var c : recent.stream().limit(5).toList()) {
                formatCorrection(sb, c);
            }
        }

        return sb.toString();
    }

    private void formatCorrection(StringBuilder sb, ClassificationCorrection c) {
        sb.append("- ");
        if (c.getCorrectionType() == CorrectionType.CATEGORY_CHANGED || c.getCorrectionType() == CorrectionType.BOTH_CHANGED) {
            sb.append("Category changed: ").append(c.getOriginalCategoryName())
                    .append(" → ").append(c.getCorrectedCategoryName());
        }
        if (c.getCorrectionType() == CorrectionType.SENSITIVITY_CHANGED || c.getCorrectionType() == CorrectionType.BOTH_CHANGED) {
            if (c.getCorrectionType() == CorrectionType.BOTH_CHANGED) sb.append("; ");
            sb.append("Sensitivity changed: ").append(c.getOriginalSensitivity())
                    .append(" → ").append(c.getCorrectedSensitivity());
        }
        if (c.getReason() != null && !c.getReason().isBlank()) {
            sb.append(" (reason: ").append(c.getReason()).append(")");
        }
        sb.append("\n");
    }
}
