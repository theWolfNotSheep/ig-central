package co.uk.wolfnotsheep.infrastructure.controllers.admin;

import co.uk.wolfnotsheep.document.models.DirectoryRoleMapping;
import co.uk.wolfnotsheep.document.repositories.DirectoryRoleMappingRepository;
import co.uk.wolfnotsheep.infrastructure.services.DocumentAccessService;
import co.uk.wolfnotsheep.platform.identity.models.UserModel;
import co.uk.wolfnotsheep.platform.identity.repositories.MongoUserRepository;
import co.uk.wolfnotsheep.platform.products.services.RolePermissionSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Admin CRUD for directory role mappings.
 * Maps external groups (Google Workspace) to internal roles.
 */
@RestController
@RequestMapping("/api/admin/directory-mappings")
public class DirectoryMappingController {

    private static final Logger log = LoggerFactory.getLogger(DirectoryMappingController.class);

    private final DirectoryRoleMappingRepository mappingRepo;
    private final MongoUserRepository userRepo;
    private final RolePermissionSyncService syncService;
    private final DocumentAccessService accessService;

    public DirectoryMappingController(DirectoryRoleMappingRepository mappingRepo,
                                       MongoUserRepository userRepo,
                                       RolePermissionSyncService syncService,
                                       DocumentAccessService accessService) {
        this.mappingRepo = mappingRepo;
        this.userRepo = userRepo;
        this.syncService = syncService;
        this.accessService = accessService;
    }

    @GetMapping
    public ResponseEntity<List<DirectoryRoleMapping>> list() {
        return ResponseEntity.ok(mappingRepo.findAll());
    }

    @PostMapping
    public ResponseEntity<DirectoryRoleMapping> create(@RequestBody DirectoryRoleMapping mapping) {
        mapping.setActive(true);
        return ResponseEntity.ok(mappingRepo.save(mapping));
    }

    @PutMapping("/{id}")
    public ResponseEntity<DirectoryRoleMapping> update(
            @PathVariable String id, @RequestBody DirectoryRoleMapping updates) {
        return mappingRepo.findById(id)
                .map(existing -> {
                    existing.setExternalGroupName(updates.getExternalGroupName());
                    existing.setExternalGroupEmail(updates.getExternalGroupEmail());
                    existing.setInternalRoleKey(updates.getInternalRoleKey());
                    existing.setSensitivityClearanceLevel(updates.getSensitivityClearanceLevel());
                    existing.setTaxonomyGrantCategoryIds(updates.getTaxonomyGrantCategoryIds());
                    existing.setActive(updates.isActive());
                    return ResponseEntity.ok(mappingRepo.save(existing));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        mappingRepo.deleteById(id);
        return ResponseEntity.ok().build();
    }

    /**
     * Apply directory mappings to all Google-authenticated users.
     * For each user with identityProvider=GOOGLE, checks if their email domain
     * matches any group mappings and applies roles/clearance/taxonomy grants.
     */
    @PostMapping("/sync")
    public ResponseEntity<Map<String, Object>> syncAll() {
        List<DirectoryRoleMapping> mappings = mappingRepo.findByDirectorySourceAndActiveTrue("GOOGLE");
        if (mappings.isEmpty()) {
            return ResponseEntity.ok(Map.of("synced", 0, "message", "No active Google mappings"));
        }

        int synced = 0;
        for (UserModel user : userRepo.findAll()) {
            if (user.getIdentity() == null || !"GOOGLE".equals(user.getIdentity().getProvider())) continue;

            boolean changed = false;
            Set<String> roles = user.getRoles() != null ? new HashSet<>(user.getRoles()) : new HashSet<>();

            for (DirectoryRoleMapping mapping : mappings) {
                // Match by email domain or specific email
                String email = user.getEmail();
                boolean matches = false;

                if (mapping.getExternalGroupEmail() != null) {
                    // Match specific group email or domain
                    if (email.endsWith("@" + mapping.getExternalGroupEmail().replace("@", ""))) {
                        matches = true; // Domain match
                    }
                    if (email.equals(mapping.getExternalGroupEmail())) {
                        matches = true; // Exact match
                    }
                }

                // Domain-wide mapping: if group name is a domain like "wolfnotsheep.co.uk"
                if (mapping.getExternalGroupName() != null && email.endsWith("@" + mapping.getExternalGroupName())) {
                    matches = true;
                }

                if (matches) {
                    // Apply role
                    if (mapping.getInternalRoleKey() != null && roles.add(mapping.getInternalRoleKey())) {
                        changed = true;
                    }
                    // Apply clearance (take highest)
                    if (mapping.getSensitivityClearanceLevel() > user.getSensitivityClearanceLevel()) {
                        user.setSensitivityClearanceLevel(mapping.getSensitivityClearanceLevel());
                        changed = true;
                    }
                    // Apply taxonomy grants
                    if (mapping.getTaxonomyGrantCategoryIds() != null) {
                        for (String catId : mapping.getTaxonomyGrantCategoryIds()) {
                            accessService.grantAccess(user.getId(), catId, true,
                                    Set.of("READ", "CREATE"), "DIRECTORY_SYNC",
                                    "Auto-granted from directory mapping: " + mapping.getExternalGroupName());
                        }
                    }
                }
            }

            if (changed) {
                user.setRoles(roles);
                userRepo.save(user);
                syncService.syncForUser(user);
                synced++;
                log.info("Directory sync applied to user: {}", user.getEmail());
            }
        }

        return ResponseEntity.ok(Map.of("synced", synced));
    }
}
