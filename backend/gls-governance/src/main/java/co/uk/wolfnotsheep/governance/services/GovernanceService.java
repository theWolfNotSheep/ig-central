package co.uk.wolfnotsheep.governance.services;

import co.uk.wolfnotsheep.governance.models.*;
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

    public GovernanceService(GovernancePolicyRepository policyRepository,
                             ClassificationCategoryRepository categoryRepository,
                             RetentionScheduleRepository retentionRepository,
                             StorageTierRepository storageTierRepository,
                             DocumentClassificationResultRepository classificationResultRepository) {
        this.policyRepository = policyRepository;
        this.categoryRepository = categoryRepository;
        this.retentionRepository = retentionRepository;
        this.storageTierRepository = storageTierRepository;
        this.classificationResultRepository = classificationResultRepository;
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
}
