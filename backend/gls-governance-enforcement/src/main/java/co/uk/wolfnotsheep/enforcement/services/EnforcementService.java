package co.uk.wolfnotsheep.enforcement.services;

import co.uk.wolfnotsheep.document.models.AuditEvent;
import co.uk.wolfnotsheep.document.models.DocumentModel;
import co.uk.wolfnotsheep.document.models.DocumentStatus;
import co.uk.wolfnotsheep.document.repositories.AuditEventRepository;
import co.uk.wolfnotsheep.document.services.DocumentService;
import co.uk.wolfnotsheep.document.services.ObjectStorageService;
import co.uk.wolfnotsheep.governance.models.GovernancePolicy;
import co.uk.wolfnotsheep.governance.models.RetentionSchedule;
import co.uk.wolfnotsheep.governance.models.StorageTier;
import co.uk.wolfnotsheep.governance.services.GovernanceService;
import co.uk.wolfnotsheep.document.events.DocumentClassifiedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@Service
public class EnforcementService {

    private static final Logger log = LoggerFactory.getLogger(EnforcementService.class);

    private final DocumentService documentService;
    private final GovernanceService governanceService;
    private final ObjectStorageService objectStorage;
    private final AuditEventRepository auditEventRepository;

    public EnforcementService(DocumentService documentService,
                              GovernanceService governanceService,
                              ObjectStorageService objectStorage,
                              AuditEventRepository auditEventRepository) {
        this.documentService = documentService;
        this.governanceService = governanceService;
        this.objectStorage = objectStorage;
        this.auditEventRepository = auditEventRepository;
    }

    public void enforce(DocumentClassifiedEvent event) {
        DocumentModel doc = documentService.getById(event.documentId());
        if (doc == null) {
            log.warn("Document not found for enforcement: {}", event.documentId());
            return;
        }

        // Apply classification results to document
        doc.setClassificationResultId(event.classificationResultId());
        doc.setCategoryId(event.categoryId());
        doc.setCategoryName(event.categoryName());
        doc.setSensitivityLabel(event.sensitivityLabel());
        doc.setTags(event.tags());
        doc.setAppliedPolicyIds(event.applicablePolicyIds());

        // Apply retention schedule
        if (event.retentionScheduleId() != null) {
            applyRetention(doc, event.retentionScheduleId());
        }

        // Migrate to correct storage tier based on sensitivity
        migrateStorageTier(doc);

        // Set final status
        if (event.requiresHumanReview()) {
            doc.setStatus(DocumentStatus.REVIEW_REQUIRED);
            log.info("Document {} flagged for human review (confidence: {})",
                    event.documentId(), event.confidence());
        } else {
            doc.setStatus(DocumentStatus.GOVERNANCE_APPLIED);
        }

        doc.setGovernanceAppliedAt(Instant.now());
        documentService.save(doc);

        // Audit trail
        auditEventRepository.save(new AuditEvent(
                doc.getId(),
                "GOVERNANCE_APPLIED",
                "SYSTEM",
                "SYSTEM",
                Map.of(
                        "category", event.categoryName(),
                        "sensitivity", event.sensitivityLabel().name(),
                        "confidence", String.valueOf(event.confidence()),
                        "requiresReview", String.valueOf(event.requiresHumanReview())
                )
        ));
    }

    private void applyRetention(DocumentModel doc, String retentionScheduleId) {
        RetentionSchedule schedule = governanceService.getRetentionSchedule(retentionScheduleId);
        if (schedule == null) {
            log.warn("Retention schedule not found: {}", retentionScheduleId);
            return;
        }

        doc.setRetentionScheduleId(retentionScheduleId);
        doc.setRetentionExpiresAt(Instant.now().plus(schedule.getRetentionDays(), ChronoUnit.DAYS));

        log.info("Applied retention to document {}: {} days (expires {})",
                doc.getId(), schedule.getRetentionDays(), doc.getRetentionExpiresAt());
    }

    private void migrateStorageTier(DocumentModel doc) {
        List<StorageTier> eligibleTiers =
                governanceService.getStorageTiersForSensitivity(doc.getSensitivityLabel());

        if (eligibleTiers.isEmpty()) {
            log.warn("No eligible storage tier for sensitivity {} — keeping document in default tier",
                    doc.getSensitivityLabel());
            return;
        }

        // Select the cheapest eligible tier
        StorageTier targetTier = eligibleTiers.stream()
                .min((a, b) -> Double.compare(a.getCostPerGbMonth(), b.getCostPerGbMonth()))
                .orElse(eligibleTiers.getFirst());

        // Only migrate if the current tier is different
        if (targetTier.getId().equals(doc.getStorageTierId())) {
            return;
        }

        String destBucket = "gls-" + targetTier.getName().toLowerCase().replace(" ", "-");
        String destKey = doc.getStorageKey();

        try {
            objectStorage.copy(doc.getStorageBucket(), doc.getStorageKey(), destBucket, destKey);
            objectStorage.delete(doc.getStorageBucket(), doc.getStorageKey());

            doc.setStorageBucket(destBucket);
            doc.setStorageTierId(targetTier.getId());

            log.info("Migrated document {} to storage tier: {} (bucket: {})",
                    doc.getId(), targetTier.getName(), destBucket);

            auditEventRepository.save(new AuditEvent(
                    doc.getId(),
                    "STORAGE_TIER_MIGRATED",
                    "SYSTEM",
                    "SYSTEM",
                    Map.of("tier", targetTier.getName(), "bucket", destBucket)
            ));
        } catch (Exception e) {
            log.error("Storage migration failed for document {}: {}", doc.getId(), e.getMessage());
        }
    }

    /**
     * Scheduled job: process documents whose retention has expired.
     * Runs daily via @Scheduled in the caller.
     */
    public void processExpiredRetentions() {
        List<DocumentModel> expired = documentService.getExpiredDocuments();
        log.info("Found {} documents with expired retention", expired.size());

        for (DocumentModel doc : expired) {
            RetentionSchedule schedule = governanceService.getRetentionSchedule(doc.getRetentionScheduleId());
            if (schedule == null) continue;

            // Don't dispose if under legal hold
            if (doc.isLegalHold()) {
                log.info("Document {} under legal hold — skipping disposition", doc.getId());
                continue;
            }

            switch (schedule.getDispositionAction()) {
                case DELETE -> {
                    objectStorage.delete(doc.getStorageBucket(), doc.getStorageKey());
                    doc.setStatus(DocumentStatus.DISPOSED);
                    documentService.save(doc);
                    auditEventRepository.save(new AuditEvent(doc.getId(), "DOCUMENT_DISPOSED",
                            "SYSTEM", "SYSTEM", Map.of("action", "DELETE", "reason", "Retention expired")));
                    log.info("Disposed (deleted) document: {}", doc.getId());
                }
                case ARCHIVE -> {
                    objectStorage.copy(doc.getStorageBucket(), doc.getStorageKey(),
                            "gls-archive", doc.getStorageKey());
                    objectStorage.delete(doc.getStorageBucket(), doc.getStorageKey());
                    doc.setStorageBucket("gls-archive");
                    doc.setStatus(DocumentStatus.ARCHIVED);
                    documentService.save(doc);
                    auditEventRepository.save(new AuditEvent(doc.getId(), "DOCUMENT_ARCHIVED",
                            "SYSTEM", "SYSTEM", Map.of("reason", "Retention expired")));
                    log.info("Archived document: {}", doc.getId());
                }
                case REVIEW -> {
                    doc.setStatus(DocumentStatus.REVIEW_REQUIRED);
                    documentService.save(doc);
                    log.info("Document {} flagged for disposition review", doc.getId());
                }
                case ANONYMISE -> {
                    doc.setExtractedText(null);
                    doc.setTags(List.of());
                    doc.setExtractedMetadata(Map.of());
                    documentService.save(doc);
                    auditEventRepository.save(new AuditEvent(doc.getId(), "DOCUMENT_ANONYMISED",
                            "SYSTEM", "SYSTEM", Map.of("reason", "Retention expired")));
                    log.info("Anonymised document metadata: {}", doc.getId());
                }
            }
        }
    }
}
