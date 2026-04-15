package co.uk.wolfnotsheep.enforcement.services;

import co.uk.wolfnotsheep.document.models.AuditEvent;
import co.uk.wolfnotsheep.document.models.DocumentModel;
import co.uk.wolfnotsheep.document.models.DocumentStatus;
import co.uk.wolfnotsheep.document.models.SystemError;
import co.uk.wolfnotsheep.document.repositories.AuditEventRepository;
import co.uk.wolfnotsheep.document.repositories.SystemErrorRepository;
import co.uk.wolfnotsheep.document.services.DocumentService;
import co.uk.wolfnotsheep.document.services.ObjectStorageService;
import co.uk.wolfnotsheep.governance.models.GovernancePolicy;
import co.uk.wolfnotsheep.governance.models.PipelineBlock;
import co.uk.wolfnotsheep.governance.models.RetentionSchedule;
import co.uk.wolfnotsheep.governance.models.SensitivityLabel;
import co.uk.wolfnotsheep.governance.models.StorageTier;
import co.uk.wolfnotsheep.governance.repositories.PipelineBlockRepository;
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
    private final PipelineBlockRepository blockRepo;
    private final SystemErrorRepository systemErrorRepo;

    public EnforcementService(DocumentService documentService,
                              GovernanceService governanceService,
                              ObjectStorageService objectStorage,
                              AuditEventRepository auditEventRepository,
                              PipelineBlockRepository blockRepo,
                              SystemErrorRepository systemErrorRepo) {
        this.documentService = documentService;
        this.governanceService = governanceService;
        this.objectStorage = objectStorage;
        this.auditEventRepository = auditEventRepository;
        this.blockRepo = blockRepo;
        this.systemErrorRepo = systemErrorRepo;
    }

    /**
     * Apply governance rules to a classified document: copy classification results,
     * extract traits/metadata, apply retention, migrate storage tier.
     *
     * Does NOT set the document's status — that is the caller's responsibility.
     * The legacy consumer sets status based on event.requiresHumanReview();
     * the execution engine lets the graph's condition/humanReview nodes decide.
     *
     * @return the updated (but not yet status-set) DocumentModel, or null if not found
     */
    public DocumentModel enforce(DocumentClassifiedEvent event) {
        DocumentModel doc = documentService.getById(event.documentId());
        if (doc == null) {
            log.warn("Document not found for enforcement: {}", event.documentId());
            return null;
        }

        // Apply classification results to document
        doc.setClassificationResultId(event.classificationResultId());
        doc.setCategoryId(event.categoryId());
        doc.setCategoryName(event.categoryName());
        doc.setSensitivityLabel(event.sensitivityLabel());
        doc.setTags(event.tags());
        doc.setAppliedPolicyIds(event.applicablePolicyIds());

        // Copy summary from classification result
        var classHistory = governanceService.getClassificationHistory(event.documentId());
        if (!classHistory.isEmpty()) {
            String summary = classHistory.getFirst().getSummary();
            if (summary != null && !summary.isBlank()) {
                doc.setSummary(summary);
            }
        }

        // Extract traits from classification result metadata
        if (!classHistory.isEmpty()) {
            var latestMeta = classHistory.getFirst().getExtractedMetadata();
            if (latestMeta != null && latestMeta.containsKey("_traits")) {
                String traitsStr = latestMeta.get("_traits");
                doc.setTraits(java.util.Arrays.stream(traitsStr.split(","))
                        .map(String::trim).filter(s -> !s.isEmpty()).toList());
                log.info("Document {} traits: {}", event.documentId(), doc.getTraits());
            }
            // Copy LLM-extracted metadata to document (excluding internal keys)
            // Always set — clears any stale Tika file metadata from extraction phase
            if (latestMeta != null && !latestMeta.isEmpty()) {
                var docMeta = new java.util.HashMap<>(latestMeta);
                docMeta.remove("_traits");
                docMeta.remove("_raw");
                doc.setExtractedMetadata(docMeta.isEmpty() ? null : docMeta);
            } else {
                doc.setExtractedMetadata(null);
            }
        }

        // Load ENFORCER block config (if available)
        boolean doRetention = true;
        boolean doStorageTier = true;
        boolean doPolicies = true;
        boolean doPiiEscalation = true;
        int piiEscalationThreshold = 5;
        List<String> configuredHighRiskTypes = null;
        List<PipelineBlock> enforcerBlocks = blockRepo.findByTypeAndActiveTrueOrderByNameAsc(
                PipelineBlock.BlockType.ENFORCER);
        if (!enforcerBlocks.isEmpty()) {
            Map<String, Object> blockContent = enforcerBlocks.getFirst().getActiveContent();
            if (blockContent != null) {
                doRetention = toBool(blockContent.get("applyRetention"), true);
                doStorageTier = toBool(blockContent.get("migrateStorageTier"), true);
                doPolicies = toBool(blockContent.get("enforcePolicies"), true);
                doPiiEscalation = toBool(blockContent.get("piiSensitivityEscalation"), true);
                piiEscalationThreshold = toInt(blockContent.get("piiEscalationThreshold"), 5);
                Object hrTypes = blockContent.get("highRiskPiiTypes");
                if (hrTypes instanceof List<?> list) {
                    configuredHighRiskTypes = list.stream().map(Object::toString).toList();
                }
            }
        }
        final List<String> highRiskPiiTypes = configuredHighRiskTypes != null
                ? configuredHighRiskTypes
                : List.of("NATIONAL_INSURANCE", "NHS_NUMBER", "PASSPORT", "DRIVING_LICENCE",
                        "CREDIT_CARD", "BANK_ACCOUNT", "SORT_CODE", "IBAN", "DATE_OF_BIRTH");

        // PII-aware enforcement: escalate sensitivity based on PII findings
        if (doPiiEscalation && doc.getPiiFindings() != null) {
            long activePiiCount = doc.getPiiFindings().stream().filter(p -> !p.isDismissed()).count();
            boolean hasHighRiskPii = doc.getPiiFindings().stream()
                    .filter(p -> !p.isDismissed())
                    .anyMatch(p -> highRiskPiiTypes.contains(p.getType()));

            SensitivityLabel currentSensitivity = doc.getSensitivityLabel();

            // Escalate to CONFIDENTIAL if high-risk PII found and currently below
            if (hasHighRiskPii && currentSensitivity != null
                    && currentSensitivity.ordinal() < SensitivityLabel.CONFIDENTIAL.ordinal()) {
                doc.setSensitivityLabel(SensitivityLabel.CONFIDENTIAL);
                log.info("Document {} sensitivity escalated to CONFIDENTIAL due to high-risk PII types",
                        event.documentId());
                auditEventRepository.save(new AuditEvent(
                        doc.getId(), "PII_SENSITIVITY_ESCALATED", "SYSTEM", "SYSTEM",
                        Map.of("from", currentSensitivity.name(), "to", "CONFIDENTIAL",
                                "reason", "High-risk PII types detected")));
            }

            // Escalate to RESTRICTED if PII count exceeds threshold
            if (activePiiCount >= piiEscalationThreshold && currentSensitivity != null
                    && doc.getSensitivityLabel().ordinal() < SensitivityLabel.RESTRICTED.ordinal()) {
                doc.setSensitivityLabel(SensitivityLabel.RESTRICTED);
                log.info("Document {} sensitivity escalated to RESTRICTED due to {} PII findings (threshold: {})",
                        event.documentId(), activePiiCount, piiEscalationThreshold);
                auditEventRepository.save(new AuditEvent(
                        doc.getId(), "PII_SENSITIVITY_ESCALATED", "SYSTEM", "SYSTEM",
                        Map.of("from", currentSensitivity.name(), "to", "RESTRICTED",
                                "reason", "PII count " + activePiiCount + " exceeds threshold " + piiEscalationThreshold)));
            }
        }

        // Apply retention schedule
        if (doRetention && event.retentionScheduleId() != null) {
            applyRetention(doc, event.retentionScheduleId());
        }

        // Migrate to correct storage tier based on sensitivity
        if (doStorageTier) {
            migrateStorageTier(doc);
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

        return doc;
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

            // Verify the copy landed before deleting the original
            if (!objectStorage.exists(destBucket, destKey)) {
                throw new RuntimeException("Copy verification failed — destination object not found in " + destBucket);
            }

            String oldBucket = doc.getStorageBucket();
            doc.setStorageBucket(destBucket);
            doc.setStorageTierId(targetTier.getId());
            documentService.save(doc);

            // Only delete original after document record points to new location
            try {
                objectStorage.delete(oldBucket, doc.getStorageKey());
            } catch (Exception delErr) {
                log.warn("Failed to delete original after migration for doc {} (orphaned in {}): {}",
                        doc.getId(), oldBucket, delErr.getMessage());
            }

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
            SystemError error = SystemError.of("ERROR", "STORAGE", "Storage migration failed for document " + doc.getId() + ": " + e.getMessage());
            error.setDocumentId(doc.getId());
            error.setService("governance-enforcer");
            systemErrorRepo.save(error);
            auditEventRepository.save(new AuditEvent(
                    doc.getId(), "STORAGE_MIGRATION_FAILED", "SYSTEM", "SYSTEM",
                    Map.of("error", e.getMessage() != null ? e.getMessage() : "unknown",
                            "targetTier", targetTier.getName())));
        }
    }

    /**
     * Scheduled job: process documents whose retention has expired.
     * Runs daily via @Scheduled in the caller.
     */
    public void processExpiredRetentions() {
        List<DocumentModel> expired = documentService.getExpiredDocuments();
        log.info("Found {} documents with expired retention", expired.size());

        int processed = 0, failed = 0;

        for (DocumentModel doc : expired) {
            try {
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
                        // Verify copy before deleting original
                        if (!objectStorage.exists("gls-archive", doc.getStorageKey())) {
                            throw new RuntimeException("Archive copy verification failed for document " + doc.getId());
                        }
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
                        doc.setPiiFindings(List.of());
                        doc.setPiiStatus("REDACTED");
                        doc.setSummary(null);
                        doc.setDublinCore(Map.of());
                        documentService.save(doc);
                        auditEventRepository.save(new AuditEvent(doc.getId(), "DOCUMENT_ANONYMISED",
                                "SYSTEM", "SYSTEM", Map.of("reason", "Retention expired")));
                        log.info("Anonymised document metadata: {}", doc.getId());
                    }
                }
                processed++;
            } catch (Exception e) {
                failed++;
                log.error("Retention disposition failed for document {}: {}", doc.getId(), e.getMessage(), e);
                SystemError error = SystemError.of("CRITICAL", "PIPELINE",
                        "Retention disposition failed for document " + doc.getId() + ": " + e.getMessage());
                error.setDocumentId(doc.getId());
                error.setService("governance-enforcer");
                systemErrorRepo.save(error);
                auditEventRepository.save(new AuditEvent(doc.getId(), "DISPOSITION_FAILED",
                        "SYSTEM", "SYSTEM",
                        Map.of("error", e.getMessage() != null ? e.getMessage() : "unknown")));
            }
        }

        log.info("Retention processing complete: {} processed, {} failed out of {} expired", processed, failed, expired.size());
        if (failed > 0) {
            SystemError summary = SystemError.of("ERROR", "PIPELINE",
                    "Retention batch: " + failed + " of " + expired.size() + " documents failed disposition");
            summary.setService("governance-enforcer");
            systemErrorRepo.save(summary);
        }
    }

    private static boolean toBool(Object val, boolean defaultVal) {
        if (val instanceof Boolean b) return b;
        if (val instanceof String s) return Boolean.parseBoolean(s);
        return defaultVal;
    }

    private static int toInt(Object val, int defaultVal) {
        if (val instanceof Number n) return n.intValue();
        if (val instanceof String s) { try { return Integer.parseInt(s); } catch (Exception e) { return defaultVal; } }
        return defaultVal;
    }
}
