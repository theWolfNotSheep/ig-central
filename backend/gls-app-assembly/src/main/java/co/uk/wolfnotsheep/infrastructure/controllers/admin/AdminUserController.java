package co.uk.wolfnotsheep.infrastructure.controllers.admin;

import co.uk.wolfnotsheep.document.models.TaxonomyGrant;
import co.uk.wolfnotsheep.infrastructure.services.DocumentAccessService;
import co.uk.wolfnotsheep.platform.identity.models.UserAccountType;
import co.uk.wolfnotsheep.platform.identity.models.UserFactory;
import co.uk.wolfnotsheep.platform.identity.models.UserModel;
import co.uk.wolfnotsheep.platform.identity.models.SignUpMethod;
import co.uk.wolfnotsheep.platform.identity.repositories.MongoUserRepository;
import co.uk.wolfnotsheep.platform.products.models.Feature;
import co.uk.wolfnotsheep.platform.products.models.Role;
import co.uk.wolfnotsheep.platform.products.repositories.FeatureRepository;
import co.uk.wolfnotsheep.platform.products.repositories.RoleRepository;
import co.uk.wolfnotsheep.platform.products.services.RolePermissionSyncService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final MongoUserRepository userRepo;
    private final UserFactory userFactory;
    private final PasswordEncoder passwordEncoder;
    private final RoleRepository roleRepo;
    private final FeatureRepository featureRepo;
    private final RolePermissionSyncService syncService;
    private final DocumentAccessService accessService;

    public AdminUserController(MongoUserRepository userRepo,
                               UserFactory userFactory,
                               PasswordEncoder passwordEncoder,
                               RoleRepository roleRepo,
                               FeatureRepository featureRepo,
                               RolePermissionSyncService syncService,
                               DocumentAccessService accessService) {
        this.userRepo = userRepo;
        this.userFactory = userFactory;
        this.passwordEncoder = passwordEncoder;
        this.roleRepo = roleRepo;
        this.featureRepo = featureRepo;
        this.syncService = syncService;
        this.accessService = accessService;
    }

    @GetMapping
    public ResponseEntity<List<UserSummary>> listUsers() {
        List<UserSummary> users = userRepo.findAll().stream()
                .map(this::toSummary)
                .toList();
        return ResponseEntity.ok(users);
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserDetail> getUser(@PathVariable String id) {
        return userRepo.findById(id)
                .map(u -> ResponseEntity.ok(toDetail(u)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<UserDetail> createUser(@RequestBody CreateUserRequest request) {
        if (userRepo.existsByEmail(request.email())) {
            return ResponseEntity.badRequest().build();
        }

        UserModel user = userFactory.createUser(request.email());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setAccountType(UserAccountType.APP_ACCOUNT);
        user.setSignUpMethod(SignUpMethod.ADMIN_CREATED);
        user.getAccountLocks().setAccountNonDisabled(true);

        // Profile
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setDisplayName(request.displayName());
        user.setDepartment(request.department());
        user.setJobTitle(request.jobTitle());

        // Apply default roles or specified roles
        if (request.roleKeys() != null && !request.roleKeys().isEmpty()) {
            user.setRoles(new HashSet<>(request.roleKeys()));
        } else {
            var defaults = syncService.getNewUserDefaults();
            user.setRoles(defaults.roles());
            user.setSensitivityClearanceLevel(defaults.sensitivityClearanceLevel());
        }

        userRepo.save(user);
        syncService.syncForUser(user);

        return ResponseEntity.ok(toDetail(userRepo.findById(user.getId()).orElse(user)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserDetail> updateUser(@PathVariable String id, @RequestBody UpdateUserRequest request) {
        return userRepo.findById(id)
                .map(user -> {
                    if (request.firstName() != null) user.setFirstName(request.firstName());
                    if (request.lastName() != null) user.setLastName(request.lastName());
                    if (request.displayName() != null) user.setDisplayName(request.displayName());
                    if (request.department() != null) user.setDepartment(request.department());
                    if (request.jobTitle() != null) user.setJobTitle(request.jobTitle());
                    userRepo.save(user);
                    return ResponseEntity.ok(toDetail(user));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/roles")
    public ResponseEntity<UserDetail> updateRoles(@PathVariable String id, @RequestBody RolesRequest request) {
        return userRepo.findById(id)
                .map(user -> {
                    user.setRoles(new HashSet<>(request.roleKeys()));
                    userRepo.save(user);
                    syncService.syncForUser(user);
                    return ResponseEntity.ok(toDetail(userRepo.findById(id).orElse(user)));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/clearance")
    public ResponseEntity<UserDetail> updateClearance(@PathVariable String id, @RequestBody ClearanceRequest request) {
        return userRepo.findById(id)
                .map(user -> {
                    user.setSensitivityClearanceLevel(request.level());
                    userRepo.save(user);
                    return ResponseEntity.ok(toDetail(user));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<UserDetail> updateStatus(@PathVariable String id, @RequestBody StatusRequest request) {
        return userRepo.findById(id)
                .map(user -> {
                    if (request.enabled() != null) user.getAccountLocks().setAccountNonDisabled(request.enabled());
                    if (request.locked() != null) user.getAccountLocks().setAccountNonLocked(!request.locked());
                    if (request.banned() != null) user.getAccountLocks().setAccountNonBanned(!request.banned());
                    userRepo.save(user);
                    return ResponseEntity.ok(toDetail(user));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(@PathVariable String id, @RequestBody Map<String, String> body) {
        return userRepo.findById(id)
                .map(user -> {
                    user.setPassword(passwordEncoder.encode(body.get("password")));
                    userRepo.save(user);
                    return ResponseEntity.ok(Map.of("status", "password_reset"));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ── Roles & Features listing ──────────────────────

    @GetMapping("/roles")
    public ResponseEntity<List<Role>> listRoles() {
        return ResponseEntity.ok(roleRepo.findByStatus("ACTIVE"));
    }

    @GetMapping("/features")
    public ResponseEntity<List<Feature>> listFeatures() {
        return ResponseEntity.ok(featureRepo.findByStatus("ACTIVE"));
    }

    @PostMapping("/roles")
    public ResponseEntity<Role> createRole(@RequestBody Role role) {
        role.setStatus("ACTIVE");
        return ResponseEntity.ok(roleRepo.save(role));
    }

    @PutMapping("/roles/{id}")
    public ResponseEntity<Role> updateRole(@PathVariable String id, @RequestBody Role updates) {
        return roleRepo.findById(id)
                .map(existing -> {
                    if (!existing.isSystemProtected()) {
                        existing.setName(updates.getName());
                        existing.setDescription(updates.getDescription());
                        existing.setFeatureIds(updates.getFeatureIds());
                        existing.setAdminRole(updates.isAdminRole());
                        existing.setDefaultForNewUsers(updates.isDefaultForNewUsers());
                        existing.setDefaultSensitivityClearance(updates.getDefaultSensitivityClearance());
                    }
                    return ResponseEntity.ok(roleRepo.save(existing));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ── Taxonomy Grants ───────────────────────────────

    @GetMapping("/{id}/taxonomy-grants")
    public ResponseEntity<List<TaxonomyGrant>> getUserTaxonomyGrants(@PathVariable String id) {
        return ResponseEntity.ok(accessService.getGrantsForUser(id));
    }

    @PostMapping("/{id}/taxonomy-grants")
    public ResponseEntity<TaxonomyGrant> grantTaxonomyAccess(
            @PathVariable String id,
            @RequestBody TaxonomyGrantRequest request,
            org.springframework.security.core.Authentication auth) {
        TaxonomyGrant grant = accessService.grantAccess(
                id, request.categoryId(), request.includeChildren(),
                request.operations(), auth != null ? auth.getName() : "ADMIN",
                request.reason());
        return ResponseEntity.ok(grant);
    }

    @PutMapping("/{userId}/taxonomy-grants/{grantId}")
    public ResponseEntity<TaxonomyGrant> updateTaxonomyGrant(
            @PathVariable String userId, @PathVariable String grantId,
            @RequestBody TaxonomyGrantUpdateRequest request) {
        TaxonomyGrant updated = accessService.updateGrant(
                grantId, request.operations(), request.includeChildren());
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{userId}/taxonomy-grants/{grantId}")
    public ResponseEntity<Void> revokeTaxonomyAccess(
            @PathVariable String userId, @PathVariable String grantId) {
        accessService.revokeAccess(grantId);
        return ResponseEntity.ok().build();
    }

    record TaxonomyGrantRequest(String categoryId, boolean includeChildren,
                                 Set<String> operations, String reason) {}

    record TaxonomyGrantUpdateRequest(Set<String> operations, Boolean includeChildren) {}

    // ── DTOs ──────────────────────────────────────────

    private UserSummary toSummary(UserModel u) {
        return new UserSummary(
                u.getId(), u.getEmail(), u.getFirstName(), u.getLastName(),
                u.getDisplayName(), u.getAvatarUrl(), u.getDepartment(), u.getJobTitle(),
                u.getRoles() != null ? List.copyOf(u.getRoles()) : List.of(),
                u.getAccountType() != null ? u.getAccountType().name() : null,
                u.isEnabled(),
                u.getSensitivityClearanceLevel(),
                u.getSignUpMethod(),
                u.getIdentity() != null ? u.getIdentity().getProvider() : null,
                u.getCreatedDate() != null ? u.getCreatedDate().toString() : null
        );
    }

    private UserDetail toDetail(UserModel u) {
        return new UserDetail(
                u.getId(), u.getEmail(), u.getFirstName(), u.getLastName(),
                u.getDisplayName(), u.getAvatarUrl(), u.getDepartment(), u.getJobTitle(),
                u.getRoles() != null ? List.copyOf(u.getRoles()) : List.of(),
                u.getPermissions() != null ? List.copyOf(u.getPermissions()) : List.of(),
                u.getAccountType() != null ? u.getAccountType().name() : null,
                u.isEnabled(),
                u.isAccountNonLocked(),
                u.getSensitivityClearanceLevel(),
                u.getSignUpMethod(),
                u.getIdentity() != null ? u.getIdentity().getProvider() : null,
                u.getCreatedDate() != null ? u.getCreatedDate().toString() : null,
                u.getLastLoginAt() != null ? u.getLastLoginAt().toString() : null
        );
    }

    record UserSummary(String id, String email, String firstName, String lastName,
                       String displayName, String avatarUrl, String department, String jobTitle,
                       List<String> roles, String accountType, boolean enabled,
                       int sensitivityClearanceLevel, String signUpMethod, String identityProvider,
                       String createdDate) {}

    record UserDetail(String id, String email, String firstName, String lastName,
                      String displayName, String avatarUrl, String department, String jobTitle,
                      List<String> roles, List<String> permissions,
                      String accountType, boolean enabled, boolean accountNonLocked,
                      int sensitivityClearanceLevel, String signUpMethod, String identityProvider,
                      String createdDate, String lastLoginAt) {}

    record CreateUserRequest(String email, String password, String firstName, String lastName,
                             String displayName, String department, String jobTitle,
                             List<String> roleKeys) {}

    record UpdateUserRequest(String firstName, String lastName, String displayName,
                             String department, String jobTitle) {}

    record RolesRequest(List<String> roleKeys) {}

    record ClearanceRequest(int level) {}

    record StatusRequest(Boolean enabled, Boolean locked, Boolean banned) {}
}
