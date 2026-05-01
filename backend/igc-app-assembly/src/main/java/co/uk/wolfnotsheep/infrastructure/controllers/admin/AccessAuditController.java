package co.uk.wolfnotsheep.infrastructure.controllers.admin;

import co.uk.wolfnotsheep.document.models.DocumentModel;
import co.uk.wolfnotsheep.document.models.TaxonomyGrant;
import co.uk.wolfnotsheep.document.services.DocumentService;
import co.uk.wolfnotsheep.governance.models.ClassificationCategory;
import co.uk.wolfnotsheep.governance.repositories.ClassificationCategoryRepository;
import co.uk.wolfnotsheep.infrastructure.services.DocumentAccessService;
import co.uk.wolfnotsheep.platform.identity.models.UserModel;
import co.uk.wolfnotsheep.platform.identity.repositories.MongoUserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Admin endpoints for auditing who has access to what.
 * Records managers can see:
 * - Who can access a specific document and why
 * - Who can access a specific category
 * - Full access matrix: users × categories
 */
@RestController
@RequestMapping("/api/admin/access")
public class AccessAuditController {

    private final MongoUserRepository userRepo;
    private final DocumentAccessService accessService;
    private final DocumentService documentService;
    private final ClassificationCategoryRepository categoryRepo;

    public AccessAuditController(MongoUserRepository userRepo,
                                  DocumentAccessService accessService,
                                  DocumentService documentService,
                                  ClassificationCategoryRepository categoryRepo) {
        this.userRepo = userRepo;
        this.accessService = accessService;
        this.documentService = documentService;
        this.categoryRepo = categoryRepo;
    }

    /**
     * Who can access a specific document and why?
     */
    @GetMapping("/document/{documentId}")
    public ResponseEntity<List<DocumentAccessEntry>> whoCanAccessDocument(@PathVariable String documentId) {
        DocumentModel doc = documentService.getById(documentId);
        if (doc == null) return ResponseEntity.notFound().build();

        List<DocumentAccessEntry> entries = new ArrayList<>();
        for (UserModel user : userRepo.findAll()) {
            if (!user.isEnabled()) continue;

            boolean isAdmin = user.getRoles() != null && user.getRoles().contains("ADMIN");
            boolean isOwner = user.getEmail().equals(doc.getUploadedBy());
            boolean hasClearance = doc.getSensitivityLabel() == null ||
                    doc.getSensitivityLabel().getLevel() <= user.getSensitivityClearanceLevel();
            boolean hasTaxonomyGrant = doc.getCategoryId() == null ||
                    accessService.getAccessibleCategoryIds(user).contains(doc.getCategoryId());

            boolean canAccess = isAdmin || (isOwner) || (hasClearance && hasTaxonomyGrant);

            if (canAccess) {
                List<String> reasons = new ArrayList<>();
                if (isAdmin) reasons.add("Admin role");
                if (isOwner) reasons.add("Document owner");
                if (hasTaxonomyGrant && !isAdmin && !isOwner) reasons.add("Taxonomy grant");
                if (hasClearance && !isAdmin) reasons.add("Clearance: " + clearanceName(user.getSensitivityClearanceLevel()));

                entries.add(new DocumentAccessEntry(
                        user.getId(), user.getEmail(),
                        user.getFirstName(), user.getLastName(),
                        user.getRoles() != null ? List.copyOf(user.getRoles()) : List.of(),
                        user.getSensitivityClearanceLevel(),
                        reasons, true
                ));
            }
        }

        return ResponseEntity.ok(entries);
    }

    /**
     * Who can access a specific category?
     */
    @GetMapping("/category/{categoryId}")
    public ResponseEntity<List<CategoryAccessEntry>> whoCanAccessCategory(@PathVariable String categoryId) {
        ClassificationCategory cat = categoryRepo.findById(categoryId).orElse(null);
        if (cat == null) return ResponseEntity.notFound().build();

        List<CategoryAccessEntry> entries = new ArrayList<>();
        for (UserModel user : userRepo.findAll()) {
            if (!user.isEnabled()) continue;

            boolean isAdmin = user.getRoles() != null && user.getRoles().contains("ADMIN");
            Set<String> accessible = accessService.getAccessibleCategoryIds(user);
            boolean hasGrant = accessible.contains("*") || accessible.contains(categoryId);

            if (isAdmin || hasGrant) {
                // Find the specific grant
                List<TaxonomyGrant> grants = accessService.getGrantsForUser(user.getId());
                String grantSource = isAdmin ? "Admin role" :
                        grants.stream()
                                .filter(g -> g.getCategoryId().equals(categoryId) || (g.isIncludeChildren() && accessible.contains(categoryId)))
                                .findFirst()
                                .map(g -> g.isIncludeChildren() ? "Inherited from parent grant" : "Direct grant")
                                .orElse("Grant");

                entries.add(new CategoryAccessEntry(
                        user.getId(), user.getEmail(),
                        user.getFirstName(), user.getLastName(),
                        user.getSensitivityClearanceLevel(),
                        grantSource,
                        grants.stream()
                                .filter(g -> accessible.contains(categoryId))
                                .map(g -> g.getOperations() != null ? List.copyOf(g.getOperations()) : List.<String>of())
                                .findFirst().orElse(isAdmin ? List.of("READ", "CREATE", "UPDATE", "DELETE") : List.of())
                ));
            }
        }

        return ResponseEntity.ok(entries);
    }

    /**
     * Full access matrix: users × categories.
     * Returns which users can access which categories.
     */
    @GetMapping("/matrix")
    public ResponseEntity<AccessMatrix> getAccessMatrix() {
        List<ClassificationCategory> categories = categoryRepo.findByActiveTrue();
        List<UserModel> users = userRepo.findAll().stream()
                .filter(UserModel::isEnabled)
                .toList();

        List<MatrixCategory> matrixCats = categories.stream()
                .map(c -> new MatrixCategory(c.getId(), c.getName(), c.getParentId()))
                .toList();

        List<MatrixUser> matrixUsers = new ArrayList<>();
        for (UserModel user : users) {
            boolean isAdmin = user.getRoles() != null && user.getRoles().contains("ADMIN");
            Set<String> accessible = accessService.getAccessibleCategoryIds(user);

            Map<String, String> categoryAccess = new LinkedHashMap<>();
            for (ClassificationCategory cat : categories) {
                if (isAdmin || accessible.contains("*") || accessible.contains(cat.getId())) {
                    // Check if sensitivity clearance allows access
                    if (cat.getDefaultSensitivity() != null &&
                            cat.getDefaultSensitivity().getLevel() > user.getSensitivityClearanceLevel()) {
                        categoryAccess.put(cat.getId(), "CLEARANCE_BLOCKED");
                    } else {
                        categoryAccess.put(cat.getId(), isAdmin ? "ADMIN" : "GRANTED");
                    }
                } else {
                    categoryAccess.put(cat.getId(), "NONE");
                }
            }

            matrixUsers.add(new MatrixUser(
                    user.getId(), user.getEmail(),
                    user.getFirstName(), user.getLastName(),
                    user.getSensitivityClearanceLevel(),
                    user.getRoles() != null ? List.copyOf(user.getRoles()) : List.of(),
                    categoryAccess
            ));
        }

        return ResponseEntity.ok(new AccessMatrix(matrixCats, matrixUsers));
    }

    private String clearanceName(int level) {
        return switch (level) {
            case 0 -> "PUBLIC";
            case 1 -> "INTERNAL";
            case 2 -> "CONFIDENTIAL";
            case 3 -> "RESTRICTED";
            default -> "UNKNOWN";
        };
    }

    record DocumentAccessEntry(String userId, String email, String firstName, String lastName,
                                List<String> roles, int clearanceLevel, List<String> reasons, boolean canAccess) {}

    record CategoryAccessEntry(String userId, String email, String firstName, String lastName,
                                int clearanceLevel, String grantSource, List<String> operations) {}

    record AccessMatrix(List<MatrixCategory> categories, List<MatrixUser> users) {}
    record MatrixCategory(String id, String name, String parentId) {}
    record MatrixUser(String userId, String email, String firstName, String lastName,
                      int clearanceLevel, List<String> roles, Map<String, String> categoryAccess) {}
}
