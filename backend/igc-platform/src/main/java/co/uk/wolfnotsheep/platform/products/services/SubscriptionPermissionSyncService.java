package co.uk.wolfnotsheep.platform.products.services;

import co.uk.wolfnotsheep.platform.identity.models.UserModel;
import co.uk.wolfnotsheep.platform.identity.repositories.MongoUserRepository;
import co.uk.wolfnotsheep.platform.products.models.Feature;
import co.uk.wolfnotsheep.platform.products.models.Product;
import co.uk.wolfnotsheep.platform.products.models.Role;
import co.uk.wolfnotsheep.platform.products.models.Subscription;
import co.uk.wolfnotsheep.platform.products.repositories.FeatureRepository;
import co.uk.wolfnotsheep.platform.products.repositories.ProductRepository;
import co.uk.wolfnotsheep.platform.products.repositories.RoleRepository;
import co.uk.wolfnotsheep.platform.products.repositories.SubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class SubscriptionPermissionSyncService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionPermissionSyncService.class);

    private final SubscriptionRepository subscriptionRepo;
    private final ProductRepository productRepo;
    private final RoleRepository roleRepo;
    private final FeatureRepository featureRepo;
    private final MongoUserRepository userRepo;

    public SubscriptionPermissionSyncService(
            SubscriptionRepository subscriptionRepo,
            ProductRepository productRepo,
            RoleRepository roleRepo,
            FeatureRepository featureRepo,
            MongoUserRepository userRepo) {
        this.subscriptionRepo = subscriptionRepo;
        this.productRepo = productRepo;
        this.roleRepo = roleRepo;
        this.featureRepo = featureRepo;
        this.userRepo = userRepo;
    }

    public void syncPermissionsForUser(String userId) {
        Optional<UserModel> userOpt = userRepo.findById(userId);
        if (userOpt.isEmpty()) {
            log.warn("Cannot sync permissions: user {} not found", userId);
            return;
        }

        UserModel user = userOpt.get();

        List<Subscription> activeSubscriptions = subscriptionRepo.findByUserIdAndStatusIn(
                userId, List.of("ACTIVE", "TRIAL"));

        Set<String> subscriptionRoles = new HashSet<>();
        Set<String> subscriptionPermissions = new HashSet<>();

        for (Subscription sub : activeSubscriptions) {
            productRepo.findById(sub.getProductId()).ifPresent(product -> {
                collectRolesAndPermissions(product, subscriptionRoles, subscriptionPermissions);
            });
        }

        // Remove old SUB_ roles, add new ones
        Set<String> currentRoles = user.getRoles() != null ? new HashSet<>(user.getRoles()) : new HashSet<>();
        currentRoles.removeIf(r -> r.startsWith("SUB_"));
        currentRoles.addAll(subscriptionRoles);
        user.setRoles(currentRoles);

        // Replace subscription permissions
        Set<String> currentPerms = user.getPermissions() != null ? new HashSet<>(user.getPermissions()) : new HashSet<>();
        currentPerms.addAll(subscriptionPermissions);
        user.setPermissions(currentPerms);

        userRepo.save(user);
        log.info("Synced permissions for user {}: {} roles, {} permissions",
                userId, subscriptionRoles.size(), subscriptionPermissions.size());
    }

    private void collectRolesAndPermissions(Product product, Set<String> roles, Set<String> permissions) {
        // Roles from product
        if (product.getRoleIds() != null && !product.getRoleIds().isEmpty()) {
            List<Role> productRoles = roleRepo.findByIdIn(product.getRoleIds());
            for (Role role : productRoles) {
                if (role.getKey() != null && role.getKey().startsWith("SUB_")) {
                    roles.add(role.getKey());
                }
                // Features from role
                if (role.getFeatureIds() != null && !role.getFeatureIds().isEmpty()) {
                    List<Feature> features = featureRepo.findByIdIn(role.getFeatureIds());
                    features.forEach(f -> permissions.add(f.getPermissionKey()));
                }
            }
        }

        // Direct features from product
        if (product.getFeatureIds() != null && !product.getFeatureIds().isEmpty()) {
            List<Feature> directFeatures = featureRepo.findByIdIn(product.getFeatureIds());
            directFeatures.forEach(f -> permissions.add(f.getPermissionKey()));
        }
    }
}
