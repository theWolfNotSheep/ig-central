package co.uk.wolfnotsheep.infrastructure.bootstrap;

import co.uk.wolfnotsheep.document.models.DocumentModel;
import co.uk.wolfnotsheep.document.models.RetentionStatus;
import co.uk.wolfnotsheep.document.repositories.DocumentRepository;
import co.uk.wolfnotsheep.governance.models.ClassificationCategory;
import co.uk.wolfnotsheep.governance.models.RetentionSchedule;
import co.uk.wolfnotsheep.governance.repositories.ClassificationCategoryRepository;
import co.uk.wolfnotsheep.governance.services.GovernanceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Backfills ISO 15489 fields on documents that were classified before
 * the model was extended. Idempotent: only updates documents missing the
 * denormalised fields, derives values from the linked ClassificationCategory.
 *
 * Runs after TaxonomyMigrationRunner (Order 3) so categories themselves
 * are guaranteed to have classification codes, levels, and paths.
 */
@Component
@Order(4)
public class DocumentISOMigrationRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DocumentISOMigrationRunner.class);

    private final DocumentRepository documentRepo;
    private final ClassificationCategoryRepository categoryRepo;
    private final GovernanceService governanceService;

    public DocumentISOMigrationRunner(DocumentRepository documentRepo,
                                      ClassificationCategoryRepository categoryRepo,
                                      GovernanceService governanceService) {
        this.documentRepo = documentRepo;
        this.categoryRepo = categoryRepo;
        this.governanceService = governanceService;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<DocumentModel> docs = documentRepo.findAll();
        int candidates = 0;
        int updated = 0;
        for (DocumentModel doc : docs) {
            if (doc.getCategoryId() == null) continue;          // never classified
            if (doc.getClassificationCode() != null) continue;  // already migrated
            candidates++;

            ClassificationCategory cat = categoryRepo.findById(doc.getCategoryId()).orElse(null);
            if (cat == null) {
                log.warn("Document {} references missing category {} — cannot migrate", doc.getId(), doc.getCategoryId());
                continue;
            }

            // Denormalise category fields onto the document
            doc.setClassificationCode(cat.getClassificationCode());
            doc.setClassificationPath(cat.getPath());
            doc.setClassificationLevel(cat.getLevel());
            doc.setJurisdiction(cat.getJurisdiction());
            doc.setLegalCitation(cat.getLegalCitation());
            doc.setCategoryPersonalData(cat.isPersonalDataFlag());
            doc.setVitalRecord(cat.isVitalRecordFlag());
            doc.setTaxonomyVersion(cat.getVersion());
            doc.setRetentionTrigger(cat.getRetentionTrigger());
            doc.setRetentionPeriodText(cat.getRetentionPeriodText());

            // Resolve disposition + retention status from schedule
            String schedId = doc.getRetentionScheduleId() != null
                    ? doc.getRetentionScheduleId()
                    : cat.getRetentionScheduleId();
            if (schedId != null) {
                RetentionSchedule sched = governanceService.getRetentionSchedule(schedId);
                if (sched != null) {
                    doc.setExpectedDispositionAction(sched.getDispositionAction());
                    if (doc.getRetentionScheduleId() == null) {
                        doc.setRetentionScheduleId(schedId);
                    }
                }
            }

            // Compute retentionStatus from existing retentionExpiresAt (best effort)
            if (doc.getRetentionStatus() == null) {
                if (doc.getRetentionExpiresAt() == null) {
                    doc.setRetentionStatus(doc.getRetentionTrigger() != null
                            && doc.getRetentionTrigger() != ClassificationCategory.RetentionTrigger.DATE_CREATED
                            && doc.getRetentionTrigger() != ClassificationCategory.RetentionTrigger.DATE_LAST_MODIFIED
                            && doc.getRetentionTrigger() != ClassificationCategory.RetentionTrigger.END_OF_FINANCIAL_YEAR
                            ? RetentionStatus.AWAITING_TRIGGER
                            : RetentionStatus.RUNNING);
                } else {
                    doc.setRetentionStatus(doc.getRetentionExpiresAt().isAfter(Instant.now())
                            ? RetentionStatus.RUNNING
                            : RetentionStatus.EXPIRED);
                }
            }

            documentRepo.save(doc);
            updated++;
        }

        if (updated > 0) {
            log.info("Document ISO 15489 migration: backfilled {} of {} candidate documents.", updated, candidates);
        } else {
            log.info("Document ISO 15489 migration: nothing to backfill (checked {} documents).", docs.size());
        }
    }
}
