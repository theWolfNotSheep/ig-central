package co.uk.wolfnotsheep.governance.services;

import co.uk.wolfnotsheep.governance.models.ClassificationCategory;
import co.uk.wolfnotsheep.governance.models.PipelineDefinition;
import co.uk.wolfnotsheep.governance.repositories.ClassificationCategoryRepository;
import co.uk.wolfnotsheep.governance.repositories.PipelineDefinitionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Resolves which pipeline should process a document based on its
 * category and mime type. Supports category inheritance — if a pipeline
 * is bound to "HR" with includeSubCategories=true, it covers "HR > Leave".
 */
@Service
public class PipelineRoutingService {

    private static final Logger log = LoggerFactory.getLogger(PipelineRoutingService.class);

    private final PipelineDefinitionRepository pipelineRepo;
    private final ClassificationCategoryRepository categoryRepo;

    public PipelineRoutingService(PipelineDefinitionRepository pipelineRepo,
                                  ClassificationCategoryRepository categoryRepo) {
        this.pipelineRepo = pipelineRepo;
        this.categoryRepo = categoryRepo;
    }

    /**
     * Find the best pipeline for a document's category and mime type.
     * Priority: direct category match > inherited (parent) match > mime type match > default.
     */
    public PipelineDefinition resolve(String categoryId, String mimeType) {
        List<PipelineDefinition> active = pipelineRepo.findByActiveTrue();
        if (active.isEmpty()) return null;

        // 1. Direct category match
        for (PipelineDefinition p : active) {
            if (p.getApplicableCategoryIds() != null && p.getApplicableCategoryIds().contains(categoryId)) {
                if (matchesMimeType(p, mimeType)) {
                    log.debug("Pipeline '{}' matched by direct category {}", p.getName(), categoryId);
                    return p;
                }
            }
        }

        // 2. Inherited match — walk up the category tree
        if (categoryId != null) {
            List<String> ancestors = getAncestorIds(categoryId);
            for (String ancestorId : ancestors) {
                for (PipelineDefinition p : active) {
                    if (p.isIncludeSubCategories()
                            && p.getApplicableCategoryIds() != null
                            && p.getApplicableCategoryIds().contains(ancestorId)
                            && matchesMimeType(p, mimeType)) {
                        log.debug("Pipeline '{}' matched by inherited category {} (ancestor of {})",
                                p.getName(), ancestorId, categoryId);
                        return p;
                    }
                }
            }
        }

        // 3. Mime type only match (pipelines with mime types but no categories)
        for (PipelineDefinition p : active) {
            if ((p.getApplicableCategoryIds() == null || p.getApplicableCategoryIds().isEmpty())
                    && p.getApplicableMimeTypes() != null && !p.getApplicableMimeTypes().isEmpty()
                    && matchesMimeType(p, mimeType)) {
                log.debug("Pipeline '{}' matched by mime type {}", p.getName(), mimeType);
                return p;
            }
        }

        // 4. Default pipeline
        return pipelineRepo.findByActiveTrueAndIsDefaultTrue()
                .orElseGet(() -> {
                    // Final fallback: first active pipeline
                    log.debug("Using first active pipeline as fallback");
                    return active.getFirst();
                });
    }

    /**
     * Check for overlapping pipeline coverage across all active pipelines.
     * Returns a map of category ID → list of pipeline names that cover it.
     */
    public Map<String, List<PipelineOverlap>> checkOverlaps() {
        List<PipelineDefinition> active = pipelineRepo.findByActiveTrue();
        Map<String, List<PipelineOverlap>> overlaps = new LinkedHashMap<>();

        // Build a map: categoryId → which pipelines cover it
        Map<String, List<PipelineOverlap>> coverage = new LinkedHashMap<>();

        for (PipelineDefinition p : active) {
            if (p.getApplicableCategoryIds() == null) continue;

            for (String catId : p.getApplicableCategoryIds()) {
                coverage.computeIfAbsent(catId, k -> new ArrayList<>())
                        .add(new PipelineOverlap(p.getId(), p.getName(), true));

                // If includeSubCategories, mark all descendants too
                if (p.isIncludeSubCategories()) {
                    for (String descendant : getDescendantIds(catId)) {
                        coverage.computeIfAbsent(descendant, k -> new ArrayList<>())
                                .add(new PipelineOverlap(p.getId(), p.getName(), false));
                    }
                }
            }
        }

        // Filter to only overlapping entries (more than one pipeline)
        for (var entry : coverage.entrySet()) {
            if (entry.getValue().size() > 1) {
                overlaps.put(entry.getKey(), entry.getValue());
            }
        }

        return overlaps;
    }

    /**
     * Get all category IDs covered by a specific pipeline (including inherited).
     */
    public Set<String> getCoveredCategories(String pipelineId) {
        return pipelineRepo.findById(pipelineId).map(p -> {
            Set<String> covered = new HashSet<>();
            if (p.getApplicableCategoryIds() != null) {
                covered.addAll(p.getApplicableCategoryIds());
                if (p.isIncludeSubCategories()) {
                    for (String catId : p.getApplicableCategoryIds()) {
                        covered.addAll(getDescendantIds(catId));
                    }
                }
            }
            return covered;
        }).orElse(Set.of());
    }

    private boolean matchesMimeType(PipelineDefinition p, String mimeType) {
        if (p.getApplicableMimeTypes() == null || p.getApplicableMimeTypes().isEmpty()) return true;
        if (mimeType == null) return true;
        return p.getApplicableMimeTypes().stream()
                .anyMatch(pattern -> mimeType.contains(pattern) || pattern.equals("*"));
    }

    private List<String> getAncestorIds(String categoryId) {
        List<String> ancestors = new ArrayList<>();
        String current = categoryId;
        int depth = 0;
        while (current != null && depth < 10) {
            String parentId = categoryRepo.findById(current)
                    .map(ClassificationCategory::getParentId)
                    .orElse(null);
            if (parentId != null) ancestors.add(parentId);
            current = parentId;
            depth++;
        }
        return ancestors;
    }

    private Set<String> getDescendantIds(String categoryId) {
        Set<String> descendants = new HashSet<>();
        Queue<String> queue = new LinkedList<>();
        queue.add(categoryId);
        while (!queue.isEmpty()) {
            String current = queue.poll();
            List<ClassificationCategory> children = categoryRepo.findByParentIdAndActiveTrue(current);
            for (ClassificationCategory child : children) {
                descendants.add(child.getId());
                queue.add(child.getId());
            }
        }
        return descendants;
    }

    /**
     * Look up a pipeline by ID (for manual selection).
     */
    public Optional<PipelineDefinition> findById(String pipelineId) {
        return pipelineRepo.findById(pipelineId);
    }

    public record PipelineOverlap(String pipelineId, String pipelineName, boolean directMatch) {}
}
