package co.uk.wolfnotsheep.infrastructure.services;

import co.uk.wolfnotsheep.document.models.DocumentModel;
import co.uk.wolfnotsheep.document.models.TaxonomyGrant;
import co.uk.wolfnotsheep.document.repositories.TaxonomyGrantRepository;
import co.uk.wolfnotsheep.governance.models.ClassificationCategory;
import co.uk.wolfnotsheep.governance.repositories.ClassificationCategoryRepository;
import co.uk.wolfnotsheep.platform.identity.models.UserModel;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

/**
 * Central access control service for documents.
 * Combines three dimensions:
 * 1. Permission (does the user's role allow this action?)
 * 2. Taxonomy (is the user granted access to this category?)
 * 3. Sensitivity (is the user cleared for this sensitivity level?)
 *
 * Admin users bypass all checks.
 */
@Service
public class DocumentAccessService {

    private final TaxonomyGrantRepository grantRepo;
    private final ClassificationCategoryRepository categoryRepo;

    public DocumentAccessService(TaxonomyGrantRepository grantRepo,
                                  ClassificationCategoryRepository categoryRepo) {
        this.grantRepo = grantRepo;
        this.categoryRepo = categoryRepo;
    }

    /**
     * Check if a user can access a specific document.
     */
    public boolean canAccess(UserModel user, DocumentModel document, String operation) {
        if (isAdmin(user)) return true;

        // Owner always has READ access to their own documents
        if ("READ".equals(operation) && user.getEmail().equals(document.getUploadedBy())) {
            return true;
        }

        // Check sensitivity clearance
        if (document.getSensitivityLabel() != null) {
            if (document.getSensitivityLabel().getLevel() > user.getSensitivityClearanceLevel()) {
                return false;
            }
        }

        // Check taxonomy access (only for classified documents)
        if (document.getCategoryId() != null) {
            Set<String> accessible = getAccessibleCategoryIds(user);
            if (!accessible.contains("*") && !accessible.contains(document.getCategoryId())) {
                return false;
            }
        }

        return true;
    }

    /**
     * Build MongoDB criteria that pre-filters documents based on user access.
     * Use this in list/search queries to avoid loading all docs and filtering in Java.
     */
    public Criteria buildAccessCriteria(UserModel user) {
        if (isAdmin(user)) return new Criteria(); // no filter

        List<Criteria> accessOr = new ArrayList<>();

        // Own documents (always visible)
        accessOr.add(Criteria.where("uploadedBy").is(user.getEmail()));

        // Documents in accessible categories at or below clearance level
        Set<String> accessibleCategories = getAccessibleCategoryIds(user);
        if (!accessibleCategories.isEmpty() && !accessibleCategories.contains("*")) {
            Criteria categoryCriteria = Criteria.where("categoryId").in(accessibleCategories);

            // Apply sensitivity filter
            if (user.getSensitivityClearanceLevel() < 3) {
                // Filter to documents at or below clearance
                List<String> allowedLabels = getAllowedSensitivityLabels(user.getSensitivityClearanceLevel());
                categoryCriteria = new Criteria().andOperator(
                        categoryCriteria,
                        Criteria.where("sensitivityLabel").in(allowedLabels)
                );
            }

            accessOr.add(categoryCriteria);
        }

        // Unclassified documents (no category) — only own
        // Already covered by uploadedBy above

        if (accessOr.size() == 1) return accessOr.getFirst();
        return new Criteria().orOperator(accessOr.toArray(new Criteria[0]));
    }

    /**
     * Get all category IDs a user can access (including inherited via parent grants).
     */
    public Set<String> getAccessibleCategoryIds(UserModel user) {
        if (isAdmin(user)) return Set.of("*");

        List<TaxonomyGrant> grants = grantRepo.findByUserId(user.getId());

        // Filter expired grants
        grants = grants.stream()
                .filter(g -> g.getExpiresAt() == null || g.getExpiresAt().isAfter(Instant.now()))
                .toList();

        if (grants.isEmpty()) return Set.of();

        Set<String> accessible = new HashSet<>();
        for (TaxonomyGrant grant : grants) {
            accessible.add(grant.getCategoryId());
            if (grant.isIncludeChildren()) {
                accessible.addAll(getDescendantCategoryIds(grant.getCategoryId()));
            }
        }

        return accessible;
    }

    /**
     * Grant a user access to a taxonomy category.
     */
    public TaxonomyGrant grantAccess(String userId, String categoryId, boolean includeChildren,
                                      Set<String> operations, String grantedBy, String reason) {
        TaxonomyGrant grant = new TaxonomyGrant();
        grant.setUserId(userId);
        grant.setCategoryId(categoryId);
        grant.setIncludeChildren(includeChildren);
        grant.setOperations(operations);
        grant.setGrantedBy(grantedBy);
        grant.setGrantedAt(Instant.now());
        grant.setReason(reason);
        return grantRepo.save(grant);
    }

    /**
     * Update operations/includeChildren on an existing grant.
     */
    public TaxonomyGrant updateGrant(String grantId, Set<String> operations, Boolean includeChildren) {
        TaxonomyGrant grant = grantRepo.findById(grantId)
                .orElseThrow(() -> new IllegalArgumentException("Grant not found: " + grantId));
        if (operations != null) grant.setOperations(operations);
        if (includeChildren != null) grant.setIncludeChildren(includeChildren);
        return grantRepo.save(grant);
    }

    /**
     * Revoke a taxonomy grant.
     */
    public void revokeAccess(String grantId) {
        grantRepo.deleteById(grantId);
    }

    /**
     * Get all grants for a user.
     */
    public List<TaxonomyGrant> getGrantsForUser(String userId) {
        return grantRepo.findByUserId(userId);
    }

    /**
     * Get all grants for a category.
     */
    public List<TaxonomyGrant> getGrantsForCategory(String categoryId) {
        return grantRepo.findByCategoryId(categoryId);
    }

    private boolean isAdmin(UserModel user) {
        return user.getRoles() != null && user.getRoles().stream()
                .anyMatch(r -> {
                    // Check if any of the user's roles is an admin role
                    // For now, check for ADMIN role. In production, check Role.isAdminRole() from DB
                    return "ADMIN".equals(r);
                });
    }

    private List<String> getAllowedSensitivityLabels(int clearanceLevel) {
        List<String> allowed = new ArrayList<>();
        if (clearanceLevel >= 0) allowed.add("PUBLIC");
        if (clearanceLevel >= 1) allowed.add("INTERNAL");
        if (clearanceLevel >= 2) allowed.add("CONFIDENTIAL");
        if (clearanceLevel >= 3) allowed.add("RESTRICTED");
        return allowed;
    }

    private Set<String> getDescendantCategoryIds(String categoryId) {
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
}
