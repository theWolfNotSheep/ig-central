package co.uk.wolfnotsheep.infrastructure.bootstrap;

import co.uk.wolfnotsheep.platform.identity.models.UserModel;
import co.uk.wolfnotsheep.platform.identity.repositories.MongoUserRepository;
import co.uk.wolfnotsheep.platform.products.models.Feature;
import co.uk.wolfnotsheep.platform.products.models.Role;
import co.uk.wolfnotsheep.platform.products.repositories.FeatureRepository;
import co.uk.wolfnotsheep.platform.products.repositories.RoleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
@Order(1) // Run alongside AdminUserSeeder (both Order 1, but this can run after)
public class PermissionDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PermissionDataSeeder.class);

    private final FeatureRepository featureRepo;
    private final RoleRepository roleRepo;
    private final MongoUserRepository userRepo;

    public PermissionDataSeeder(FeatureRepository featureRepo, RoleRepository roleRepo,
                                MongoUserRepository userRepo) {
        this.featureRepo = featureRepo;
        this.roleRepo = roleRepo;
        this.userRepo = userRepo;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (featureRepo.count() > 0) {
            log.info("Features already seeded, skipping permission seeder.");
            syncAdminPermissions();
            return;
        }

        Map<String, String> featureIds = seedFeatures();
        seedRoles(featureIds);
        syncAdminPermissions();

        log.info("Permission data seeder complete.");
    }

    private Map<String, String> seedFeatures() {
        List<Feature> features = List.of(
                // Documents
                feature("DOCUMENT_CREATE", "Create Document", "Upload new documents", "documents"),
                feature("DOCUMENT_READ", "View Document", "View document details and content", "documents"),
                feature("DOCUMENT_UPDATE", "Update Document", "Edit document metadata", "documents"),
                feature("DOCUMENT_DELETE", "Delete Document", "Remove documents from the system", "documents"),
                feature("DOCUMENT_DOWNLOAD", "Download Document", "Download document files", "documents"),
                feature("DOCUMENT_REPROCESS", "Reprocess Document", "Re-queue documents through the pipeline", "documents"),

                // Taxonomy
                feature("TAXONOMY_CREATE", "Create Category", "Add taxonomy categories", "taxonomy"),
                feature("TAXONOMY_READ", "View Taxonomy", "View the classification taxonomy", "taxonomy"),
                feature("TAXONOMY_UPDATE", "Update Category", "Edit taxonomy categories", "taxonomy"),
                feature("TAXONOMY_DELETE", "Delete Category", "Deactivate taxonomy categories", "taxonomy"),

                // Governance Policies
                feature("GOV_POLICY_CREATE", "Create Policy", "Create governance policies", "governance"),
                feature("GOV_POLICY_READ", "View Policies", "View governance policies", "governance"),
                feature("GOV_POLICY_UPDATE", "Update Policy", "Edit governance policies", "governance"),
                feature("GOV_POLICY_DELETE", "Delete Policy", "Deactivate governance policies", "governance"),

                // Retention
                feature("RETENTION_CREATE", "Create Retention Schedule", "Create retention schedules", "governance"),
                feature("RETENTION_READ", "View Retention Schedules", "View retention schedules", "governance"),
                feature("RETENTION_UPDATE", "Update Retention Schedule", "Edit retention schedules", "governance"),

                // Storage Tiers
                feature("STORAGE_TIER_READ", "View Storage Tiers", "View storage tier configuration", "governance"),
                feature("STORAGE_TIER_UPDATE", "Update Storage Tiers", "Edit storage tier configuration", "governance"),

                // PII Types
                feature("PII_TYPE_READ", "View PII Types", "View PII type definitions", "governance"),
                feature("PII_TYPE_CREATE", "Create PII Type", "Add new PII type definitions", "governance"),
                feature("PII_TYPE_UPDATE", "Update PII Type", "Edit PII type definitions", "governance"),
                feature("PII_TYPE_APPROVE", "Approve PII Type", "Approve user-submitted PII types", "governance"),

                // Metadata Schemas
                feature("METADATA_READ", "View Metadata Schemas", "View metadata extraction schemas", "governance"),
                feature("METADATA_CREATE", "Create Metadata Schema", "Create metadata schemas", "governance"),
                feature("METADATA_UPDATE", "Update Metadata Schema", "Edit metadata schemas", "governance"),

                // Review
                feature("REVIEW_READ", "View Review Queue", "Access the review queue", "review"),
                feature("REVIEW_ACTION", "Take Review Actions", "Approve, reject, override, flag PII", "review"),

                // Users
                feature("USER_CREATE", "Create User", "Create new user accounts", "users"),
                feature("USER_READ", "View Users", "View user list and profiles", "users"),
                feature("USER_UPDATE", "Update User", "Edit user profiles", "users"),
                feature("USER_DELETE", "Delete User", "Deactivate user accounts", "users"),
                feature("USER_ROLES_ASSIGN", "Assign Roles", "Assign roles to users", "users"),
                feature("USER_CLEARANCE_SET", "Set Clearance", "Set user sensitivity clearance level", "users"),
                feature("USER_TAXONOMY_ASSIGN", "Assign Taxonomy Access", "Grant/revoke taxonomy access", "users"),

                // Pipeline
                feature("PIPELINE_READ", "View Pipelines", "View pipeline definitions", "pipeline"),
                feature("PIPELINE_UPDATE", "Update Pipelines", "Edit pipeline definitions", "pipeline"),

                // Monitoring
                feature("MONITORING_READ", "View Monitoring", "Access system monitoring", "monitoring"),
                feature("MONITORING_ACTION", "Monitoring Actions", "Reset stale, retry failed, purge queues", "monitoring"),

                // Settings
                feature("SETTINGS_READ", "View Settings", "View application settings", "settings"),
                feature("SETTINGS_UPDATE", "Update Settings", "Edit application settings", "settings"),

                // Search
                feature("SEARCH_USE", "Use Search", "Search documents and metadata", "search")
        );

        featureRepo.saveAll(features);
        log.info("Seeded {} features", features.size());

        return features.stream().collect(Collectors.toMap(Feature::getPermissionKey, Feature::getId));
    }

    private void seedRoles(Map<String, String> featureIds) {
        List<Role> roles = List.of(
                role("ADMIN", "System Administrator", "Full system access",
                        true, true, false, 3,
                        featureIds.values().stream().toList()),

                role("COMPLIANCE_OFFICER", "Compliance Officer", "Governance, review, and monitoring access",
                        false, false, false, 3,
                        resolveFeatureIds(featureIds,
                                "GOV_POLICY_READ", "GOV_POLICY_CREATE", "GOV_POLICY_UPDATE",
                                "RETENTION_READ", "RETENTION_CREATE", "RETENTION_UPDATE",
                                "STORAGE_TIER_READ", "PII_TYPE_READ", "PII_TYPE_APPROVE",
                                "METADATA_READ", "METADATA_CREATE", "METADATA_UPDATE",
                                "REVIEW_READ", "REVIEW_ACTION",
                                "DOCUMENT_READ", "DOCUMENT_DOWNLOAD",
                                "TAXONOMY_READ", "MONITORING_READ",
                                "SEARCH_USE")),

                role("DOCUMENT_MANAGER", "Document Manager", "Document lifecycle management",
                        false, false, false, 2,
                        resolveFeatureIds(featureIds,
                                "DOCUMENT_CREATE", "DOCUMENT_READ", "DOCUMENT_UPDATE",
                                "DOCUMENT_DOWNLOAD", "DOCUMENT_REPROCESS",
                                "TAXONOMY_READ", "REVIEW_READ", "REVIEW_ACTION",
                                "PII_TYPE_READ", "METADATA_READ",
                                "SEARCH_USE")),

                role("STANDARD_USER", "Standard User", "Basic document access",
                        false, false, true, 1,
                        resolveFeatureIds(featureIds,
                                "DOCUMENT_CREATE", "DOCUMENT_READ", "DOCUMENT_DOWNLOAD",
                                "TAXONOMY_READ", "PII_TYPE_READ",
                                "SEARCH_USE"))
        );

        roleRepo.saveAll(roles);
        log.info("Seeded {} roles", roles.size());
    }

    /**
     * Ensure the admin user has the ADMIN role from DB and all permissions synced.
     */
    private void syncAdminPermissions() {
        List<Role> adminRoles = roleRepo.findByAdminRoleTrueAndStatus("ACTIVE");
        if (adminRoles.isEmpty()) return;

        Role adminRole = adminRoles.getFirst();
        List<Feature> allFeatures = featureRepo.findByStatus("ACTIVE");
        Set<String> allPermissions = allFeatures.stream()
                .map(Feature::getPermissionKey)
                .collect(Collectors.toSet());

        // Find all users with the admin role and sync their permissions
        for (UserModel user : userRepo.findAll()) {
            if (user.getRoles() != null && user.getRoles().contains(adminRole.getKey())) {
                user.setPermissions(allPermissions);
                user.setSensitivityClearanceLevel(adminRole.getDefaultSensitivityClearance());
                userRepo.save(user);
                log.info("Synced admin permissions for user: {}", user.getEmail());
            }
        }
    }

    private Feature feature(String key, String name, String description, String category) {
        Feature f = new Feature();
        f.setPermissionKey(key);
        f.setName(name);
        f.setDescription(description);
        f.setCategory(category);
        f.setStatus("ACTIVE");
        return f;
    }

    private Role role(String key, String name, String description,
                      boolean adminRole, boolean systemProtected, boolean defaultForNewUsers,
                      int sensitivityClearance, List<String> featureIds) {
        Role r = new Role();
        r.setKey(key);
        r.setName(name);
        r.setDescription(description);
        r.setAdminRole(adminRole);
        r.setSystemProtected(systemProtected);
        r.setDefaultForNewUsers(defaultForNewUsers);
        r.setDefaultSensitivityClearance(sensitivityClearance);
        r.setFeatureIds(featureIds);
        r.setStatus("ACTIVE");
        return r;
    }

    private List<String> resolveFeatureIds(Map<String, String> featureIds, String... keys) {
        return Arrays.stream(keys)
                .map(featureIds::get)
                .filter(Objects::nonNull)
                .toList();
    }
}
