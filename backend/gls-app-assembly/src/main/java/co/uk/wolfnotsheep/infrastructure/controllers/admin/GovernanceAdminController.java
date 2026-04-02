package co.uk.wolfnotsheep.infrastructure.controllers.admin;

import co.uk.wolfnotsheep.governance.models.*;
import co.uk.wolfnotsheep.governance.repositories.*;
import co.uk.wolfnotsheep.governance.services.GovernanceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

/**
 * Admin CRUD endpoints for managing the governance framework.
 * These endpoints require ADMIN role.
 */
@RestController
@RequestMapping("/api/admin/governance")
public class GovernanceAdminController {

    private final GovernanceService governanceService;
    private final GovernancePolicyRepository policyRepository;
    private final ClassificationCategoryRepository categoryRepository;
    private final RetentionScheduleRepository retentionRepository;
    private final StorageTierRepository storageTierRepository;

    public GovernanceAdminController(GovernanceService governanceService,
                                     GovernancePolicyRepository policyRepository,
                                     ClassificationCategoryRepository categoryRepository,
                                     RetentionScheduleRepository retentionRepository,
                                     StorageTierRepository storageTierRepository) {
        this.governanceService = governanceService;
        this.policyRepository = policyRepository;
        this.categoryRepository = categoryRepository;
        this.retentionRepository = retentionRepository;
        this.storageTierRepository = storageTierRepository;
    }

    // ── Policies ─────────────────────────────────────────

    @GetMapping("/policies")
    public ResponseEntity<List<GovernancePolicy>> listPolicies() {
        return ResponseEntity.ok(governanceService.getActivePolicies());
    }

    @PostMapping("/policies")
    public ResponseEntity<GovernancePolicy> createPolicy(@RequestBody GovernancePolicy policy) {
        policy.setCreatedAt(Instant.now());
        policy.setUpdatedAt(Instant.now());
        policy.setVersion(1);
        policy.setActive(true);
        return ResponseEntity.ok(policyRepository.save(policy));
    }

    @PutMapping("/policies/{id}")
    public ResponseEntity<GovernancePolicy> updatePolicy(
            @PathVariable String id, @RequestBody GovernancePolicy updates) {
        return policyRepository.findById(id)
                .map(existing -> {
                    existing.setName(updates.getName());
                    existing.setDescription(updates.getDescription());
                    existing.setRules(updates.getRules());
                    existing.setApplicableCategoryIds(updates.getApplicableCategoryIds());
                    existing.setApplicableSensitivities(updates.getApplicableSensitivities());
                    existing.setEnforcementActions(updates.getEnforcementActions());
                    existing.setEffectiveFrom(updates.getEffectiveFrom());
                    existing.setEffectiveUntil(updates.getEffectiveUntil());
                    existing.setVersion(existing.getVersion() + 1);
                    existing.setUpdatedAt(Instant.now());
                    return ResponseEntity.ok(policyRepository.save(existing));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/policies/{id}")
    public ResponseEntity<Void> deactivatePolicy(@PathVariable String id) {
        return policyRepository.findById(id)
                .map(policy -> {
                    policy.setActive(false);
                    policy.setUpdatedAt(Instant.now());
                    policyRepository.save(policy);
                    return ResponseEntity.ok().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ── Classification Taxonomy ──────────────────────────

    @GetMapping("/taxonomy")
    public ResponseEntity<List<ClassificationCategory>> listCategories() {
        return ResponseEntity.ok(governanceService.getFullTaxonomy());
    }

    @GetMapping("/taxonomy/tree")
    public ResponseEntity<String> getTaxonomyTree() {
        return ResponseEntity.ok(governanceService.getTaxonomyAsText());
    }

    @PostMapping("/taxonomy")
    public ResponseEntity<ClassificationCategory> createCategory(@RequestBody ClassificationCategory category) {
        category.setActive(true);
        return ResponseEntity.ok(categoryRepository.save(category));
    }

    @PutMapping("/taxonomy/{id}")
    public ResponseEntity<ClassificationCategory> updateCategory(
            @PathVariable String id, @RequestBody ClassificationCategory updates) {
        return categoryRepository.findById(id)
                .map(existing -> {
                    existing.setName(updates.getName());
                    existing.setDescription(updates.getDescription());
                    existing.setParentId(updates.getParentId());
                    existing.setKeywords(updates.getKeywords());
                    existing.setDefaultSensitivity(updates.getDefaultSensitivity());
                    existing.setRetentionScheduleId(updates.getRetentionScheduleId());
                    return ResponseEntity.ok(categoryRepository.save(existing));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/taxonomy/{id}")
    public ResponseEntity<Void> deactivateCategory(@PathVariable String id) {
        return categoryRepository.findById(id)
                .map(cat -> {
                    cat.setActive(false);
                    categoryRepository.save(cat);
                    return ResponseEntity.ok().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ── Retention Schedules ──────────────────────────────

    @GetMapping("/retention")
    public ResponseEntity<List<RetentionSchedule>> listRetentionSchedules() {
        return ResponseEntity.ok(governanceService.getAllRetentionSchedules());
    }

    @PostMapping("/retention")
    public ResponseEntity<RetentionSchedule> createRetentionSchedule(@RequestBody RetentionSchedule schedule) {
        return ResponseEntity.ok(retentionRepository.save(schedule));
    }

    @PutMapping("/retention/{id}")
    public ResponseEntity<RetentionSchedule> updateRetentionSchedule(
            @PathVariable String id, @RequestBody RetentionSchedule updates) {
        return retentionRepository.findById(id)
                .map(existing -> {
                    existing.setName(updates.getName());
                    existing.setDescription(updates.getDescription());
                    existing.setRetentionDays(updates.getRetentionDays());
                    existing.setDispositionAction(updates.getDispositionAction());
                    existing.setLegalHoldOverride(updates.isLegalHoldOverride());
                    existing.setRegulatoryBasis(updates.getRegulatoryBasis());
                    return ResponseEntity.ok(retentionRepository.save(existing));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ── Storage Tiers ────────────────────────────────────

    @GetMapping("/storage-tiers")
    public ResponseEntity<List<StorageTier>> listStorageTiers() {
        return ResponseEntity.ok(governanceService.getAllStorageTiers());
    }

    @PostMapping("/storage-tiers")
    public ResponseEntity<StorageTier> createStorageTier(@RequestBody StorageTier tier) {
        return ResponseEntity.ok(storageTierRepository.save(tier));
    }

    @PutMapping("/storage-tiers/{id}")
    public ResponseEntity<StorageTier> updateStorageTier(
            @PathVariable String id, @RequestBody StorageTier updates) {
        return storageTierRepository.findById(id)
                .map(existing -> {
                    existing.setName(updates.getName());
                    existing.setDescription(updates.getDescription());
                    existing.setEncryptionType(updates.getEncryptionType());
                    existing.setImmutable(updates.isImmutable());
                    existing.setGeographicallyRestricted(updates.isGeographicallyRestricted());
                    existing.setRegion(updates.getRegion());
                    existing.setAllowedSensitivities(updates.getAllowedSensitivities());
                    existing.setMaxFileSizeBytes(updates.getMaxFileSizeBytes());
                    existing.setCostPerGbMonth(updates.getCostPerGbMonth());
                    return ResponseEntity.ok(storageTierRepository.save(existing));
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
