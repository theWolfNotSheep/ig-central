package co.uk.wolfnotsheep.infrastructure.bootstrap;

import co.uk.wolfnotsheep.governance.models.ClassificationCategory;
import co.uk.wolfnotsheep.governance.models.ClassificationCategory.NodeStatus;
import co.uk.wolfnotsheep.governance.models.ClassificationCategory.TaxonomyLevel;
import co.uk.wolfnotsheep.governance.models.RetentionSchedule;
import co.uk.wolfnotsheep.governance.repositories.ClassificationCategoryRepository;
import co.uk.wolfnotsheep.governance.repositories.RetentionScheduleRepository;
import co.uk.wolfnotsheep.governance.services.ClassificationCodeGenerator;
import co.uk.wolfnotsheep.governance.services.GovernanceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Backfills ISO 15489 fields on existing taxonomy nodes and retention schedules.
 * Runs after GovernanceDataSeeder (Order 3) to handle pre-existing data
 * that was created before the ISO 15489 alignment.
 *
 * Safe to run repeatedly — only updates records with missing fields.
 */
@Component
@Order(3)
public class TaxonomyMigrationRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TaxonomyMigrationRunner.class);

    private final ClassificationCategoryRepository categoryRepo;
    private final RetentionScheduleRepository retentionRepo;
    private final GovernanceService governanceService;

    public TaxonomyMigrationRunner(ClassificationCategoryRepository categoryRepo,
                                   RetentionScheduleRepository retentionRepo,
                                   GovernanceService governanceService) {
        this.categoryRepo = categoryRepo;
        this.retentionRepo = retentionRepo;
        this.governanceService = governanceService;
    }

    @Override
    public void run(ApplicationArguments args) {
        int migrated = 0;

        migrated += migrateCategories();
        migrated += migrateRetentionSchedules();

        if (migrated > 0) {
            // Rebuild materialised paths after migration
            governanceService.rebuildPaths();
            log.info("Taxonomy migration complete — {} records updated, paths rebuilt.", migrated);
        } else {
            log.info("Taxonomy migration: no records needed updating.");
        }
    }

    private int migrateCategories() {
        List<ClassificationCategory> all = categoryRepo.findAll();
        if (all.isEmpty()) return 0;

        // Build parent lookup for code generation
        Map<String, ClassificationCategory> byId = all.stream()
                .collect(Collectors.toMap(ClassificationCategory::getId, c -> c));

        int updated = 0;

        for (ClassificationCategory cat : all) {
            boolean changed = false;

            // Backfill status from legacy active field
            if (cat.getStatus() == null) {
                cat.setStatus(NodeStatus.ACTIVE);
                changed = true;
            }

            // Backfill classification code
            if (cat.getClassificationCode() == null || cat.getClassificationCode().isBlank()) {
                String parentCode = null;
                if (cat.getParentId() != null) {
                    ClassificationCategory parent = byId.get(cat.getParentId());
                    if (parent != null) {
                        parentCode = parent.getClassificationCode();
                    }
                }
                String code = ClassificationCodeGenerator.generate(cat.getName(), parentCode);
                // Ensure uniqueness
                int suffix = 1;
                String candidate = code;
                while (categoryRepo.existsByClassificationCode(candidate)) {
                    candidate = ClassificationCodeGenerator.withSuffix(code, suffix++);
                }
                cat.setClassificationCode(candidate);
                changed = true;
            }

            // Backfill taxonomy level
            if (cat.getLevel() == null) {
                if (cat.getParentId() == null) {
                    cat.setLevel(TaxonomyLevel.FUNCTION);
                } else {
                    // Check if this node has children — if so, it's an ACTIVITY
                    ClassificationCategory parent = byId.get(cat.getParentId());
                    if (parent != null && parent.getParentId() != null) {
                        cat.setLevel(TaxonomyLevel.TRANSACTION);
                    } else {
                        cat.setLevel(TaxonomyLevel.ACTIVITY);
                    }
                }
                changed = true;
            }

            // Backfill jurisdiction (existing seed data is UK)
            if (cat.getJurisdiction() == null || cat.getJurisdiction().isBlank()) {
                cat.setJurisdiction("UK");
                changed = true;
            }

            // Backfill version
            if (cat.getVersion() == 0) {
                cat.setVersion(1);
                changed = true;
            }

            if (changed) {
                categoryRepo.save(cat);
                updated++;
            }
        }

        if (updated > 0) {
            log.info("Migrated {} classification categories with ISO 15489 fields.", updated);
        }
        return updated;
    }

    private int migrateRetentionSchedules() {
        List<RetentionSchedule> all = retentionRepo.findAll();
        int updated = 0;

        for (RetentionSchedule rs : all) {
            boolean changed = false;

            // Backfill ISO 8601 duration from retentionDays
            if (rs.getRetentionDuration() == null || rs.getRetentionDuration().isBlank()) {
                int days = rs.getRetentionDays();
                if (days < 0) {
                    rs.setRetentionDuration("PERMANENT");
                } else if (days % 365 == 0) {
                    rs.setRetentionDuration("P" + (days / 365) + "Y");
                } else if (days % 30 == 0) {
                    rs.setRetentionDuration("P" + (days / 30) + "M");
                } else {
                    rs.setRetentionDuration("P" + days + "D");
                }
                changed = true;
            }

            // Backfill jurisdiction
            if (rs.getJurisdiction() == null || rs.getJurisdiction().isBlank()) {
                rs.setJurisdiction("UK");
                changed = true;
            }

            // Backfill default retention trigger
            if (rs.getRetentionTrigger() == null || rs.getRetentionTrigger().isBlank()) {
                rs.setRetentionTrigger("DATE_CREATED");
                changed = true;
            }

            if (changed) {
                retentionRepo.save(rs);
                updated++;
            }
        }

        if (updated > 0) {
            log.info("Migrated {} retention schedules with ISO 8601 durations.", updated);
        }
        return updated;
    }
}
