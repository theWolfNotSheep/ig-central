package co.uk.wolfnotsheep.platform.products.services;

import co.uk.wolfnotsheep.platform.identity.models.UserModel;
import co.uk.wolfnotsheep.platform.identity.repositories.MongoUserRepository;
import co.uk.wolfnotsheep.platform.products.models.Feature;
import co.uk.wolfnotsheep.platform.products.models.Role;
import co.uk.wolfnotsheep.platform.products.repositories.FeatureRepository;
import co.uk.wolfnotsheep.platform.products.repositories.RoleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Syncs a user's permissions based on their assigned roles.
 * For each role key in user.roles, loads the Role from DB,
 * resolves its featureIds to permissionKeys, and sets them on the user.
 */
@Service
public class RolePermissionSyncService {

    private static final Logger log = LoggerFactory.getLogger(RolePermissionSyncService.class);

    private final RoleRepository roleRepo;
    private final FeatureRepository featureRepo;
    private final MongoUserRepository userRepo;

    public RolePermissionSyncService(RoleRepository roleRepo, FeatureRepository featureRepo,
                                     MongoUserRepository userRepo) {
        this.roleRepo = roleRepo;
        this.featureRepo = featureRepo;
        this.userRepo = userRepo;
    }

    /**
     * Sync permissions for a specific user based on their current roles.
     */
    public void syncForUser(String userId) {
        userRepo.findById(userId).ifPresent(this::syncForUser);
    }

    public void syncForUser(UserModel user) {
        if (user.getRoles() == null || user.getRoles().isEmpty()) {
            user.setPermissions(Set.of());
            userRepo.save(user);
            return;
        }

        Set<String> permissions = resolvePermissions(user.getRoles());
        user.setPermissions(permissions);
        userRepo.save(user);
        log.debug("Synced {} permissions for user {} from {} roles",
                permissions.size(), user.getEmail(), user.getRoles().size());
    }

    /**
     * Resolve a set of role keys to their combined permission keys.
     */
    public Set<String> resolvePermissions(Set<String> roleKeys) {
        Set<String> permissions = new HashSet<>();
        Set<String> allFeatureIds = new HashSet<>();

        for (String roleKey : roleKeys) {
            roleRepo.findByKey(roleKey).ifPresent(role -> {
                if (role.getFeatureIds() != null) {
                    allFeatureIds.addAll(role.getFeatureIds());
                }
            });
        }

        if (!allFeatureIds.isEmpty()) {
            List<Feature> features = featureRepo.findByIdIn(new ArrayList<>(allFeatureIds));
            features.forEach(f -> permissions.add(f.getPermissionKey()));
        }

        return permissions;
    }

    /**
     * Get the default roles and clearance for new users (from roles marked as defaultForNewUsers).
     */
    public NewUserDefaults getNewUserDefaults() {
        List<Role> defaultRoles = roleRepo.findByDefaultForNewUsersTrueAndStatus("ACTIVE");
        Set<String> roleKeys = defaultRoles.stream().map(Role::getKey).collect(Collectors.toSet());
        int clearance = defaultRoles.stream()
                .mapToInt(Role::getDefaultSensitivityClearance)
                .max().orElse(0);
        Set<String> permissions = resolvePermissions(roleKeys);
        return new NewUserDefaults(roleKeys, permissions, clearance);
    }

    public record NewUserDefaults(Set<String> roles, Set<String> permissions, int sensitivityClearanceLevel) {}
}
