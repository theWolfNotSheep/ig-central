package co.uk.wolfnotsheep.governance.services;

import co.uk.wolfnotsheep.governance.models.*;
import co.uk.wolfnotsheep.governance.models.ClassificationCorrection.CorrectionType;
import co.uk.wolfnotsheep.governance.repositories.*;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
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

    public GovernanceService(GovernancePolicyRepository policyRepository,
                             ClassificationCategoryRepository categoryRepository,
                             RetentionScheduleRepository retentionRepository,
                             StorageTierRepository storageTierRepository,
                             DocumentClassificationResultRepository classificationResultRepository,
                             ClassificationCorrectionRepository correctionRepository,
                             MetadataSchemaRepository metadataSchemaRepository) {
        this.policyRepository = policyRepository;
        this.categoryRepository = categoryRepository;
        this.retentionRepository = retentionRepository;
        this.storageTierRepository = storageTierRepository;
        this.classificationResultRepository = classificationResultRepository;
        this.correctionRepository = correctionRepository;
        this.metadataSchemaRepository = metadataSchemaRepository;
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
     * Returns a formatted string the LLM can reason over.
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
                .append("- ").append(node.getName())
                .append(": ").append(node.getDescription())
                .append(" [default sensitivity: ").append(node.getDefaultSensitivity()).append("]")
                .append("\n");

        for (ClassificationCategory child : byParent.getOrDefault(node.getId(), List.of())) {
            buildTaxonomyTree(sb, child, byParent, depth + 1);
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
        return correctionRepository.findByCorrectionTypeOrderByCorrectedAtDesc(CorrectionType.PII_FLAGGED);
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
        return correctionRepository.findByCorrectionTypeOrderByCorrectedAtDesc(CorrectionType.PII_DISMISSED);
    }

    /**
     * Build a text summary of recent corrections for LLM consumption.
     * This is the key feedback mechanism — the LLM reads past human corrections
     * to improve its accuracy on similar documents.
     */
    public String getCorrectionsSummaryForLlm(String categoryId, String mimeType) {
        StringBuilder sb = new StringBuilder();

        // Get corrections relevant to this category
        List<ClassificationCorrection> byCat = categoryId != null
                ? correctionRepository.findByOriginalCategoryIdOrderByCorrectedAtDesc(categoryId)
                : List.of();

        // Get corrections for this mime type
        List<ClassificationCorrection> byMime = mimeType != null
                ? correctionRepository.findByMimeTypeOrderByCorrectedAtDesc(mimeType)
                : List.of();

        // Get recent PII corrections
        List<ClassificationCorrection> pii = correctionRepository
                .findByCorrectionTypeOrderByCorrectedAtDesc(CorrectionType.PII_FLAGGED);

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
