package co.uk.wolfnotsheep.infrastructure.controllers.review;

import co.uk.wolfnotsheep.document.models.AuditEvent;
import co.uk.wolfnotsheep.document.models.DocumentModel;
import co.uk.wolfnotsheep.document.models.DocumentStatus;
import co.uk.wolfnotsheep.document.repositories.AuditEventRepository;
import co.uk.wolfnotsheep.document.services.DocumentService;
import co.uk.wolfnotsheep.governance.models.BlockFeedback;
import co.uk.wolfnotsheep.governance.models.ClassificationCorrection;
import co.uk.wolfnotsheep.governance.models.ClassificationCorrection.CorrectionType;
import co.uk.wolfnotsheep.governance.models.DocumentClassificationResult;
import co.uk.wolfnotsheep.governance.models.PipelineBlock;
import co.uk.wolfnotsheep.governance.models.SensitivityLabel;
import co.uk.wolfnotsheep.governance.models.ClassificationCategory;
import co.uk.wolfnotsheep.governance.models.RetentionSchedule;
import co.uk.wolfnotsheep.governance.repositories.BlockFeedbackRepository;
import co.uk.wolfnotsheep.governance.repositories.ClassificationCategoryRepository;
import co.uk.wolfnotsheep.governance.repositories.PipelineBlockRepository;
import co.uk.wolfnotsheep.governance.services.GovernanceService;
import co.uk.wolfnotsheep.infrastructure.audit.PlatformAuditEmitter;
import co.uk.wolfnotsheep.infrastructure.services.BertTrainingDataCollector;
import co.uk.wolfnotsheep.platformaudit.envelope.Outcome;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Human review queue for low-confidence LLM classifications.
 * Reviewers can approve, override, or reject classifications.
 */
@RestController
@RequestMapping("/api/review")
public class ReviewQueueController {

    private final DocumentService documentService;
    private final GovernanceService governanceService;
    private final AuditEventRepository auditEventRepository;
    private final BlockFeedbackRepository blockFeedbackRepo;
    private final PipelineBlockRepository blockRepo;
    private final ClassificationCategoryRepository categoryRepo;
    private final BertTrainingDataCollector bertCollector;
    private final PlatformAuditEmitter platformAudit;

    public ReviewQueueController(DocumentService documentService,
                                 GovernanceService governanceService,
                                 AuditEventRepository auditEventRepository,
                                 BlockFeedbackRepository blockFeedbackRepo,
                                 PipelineBlockRepository blockRepo,
                                 ClassificationCategoryRepository categoryRepo,
                                 BertTrainingDataCollector bertCollector,
                                 PlatformAuditEmitter platformAudit) {
        this.documentService = documentService;
        this.governanceService = governanceService;
        this.auditEventRepository = auditEventRepository;
        this.blockFeedbackRepo = blockFeedbackRepo;
        this.blockRepo = blockRepo;
        this.categoryRepo = categoryRepo;
        this.platformAudit = platformAudit;
        this.bertCollector = bertCollector;
    }

    @GetMapping
    public ResponseEntity<Page<DocumentModel>> getReviewQueue(Pageable pageable) {
        return ResponseEntity.ok(documentService.getReviewQueue(pageable));
    }

    @GetMapping("/{documentId}/classification")
    public ResponseEntity<List<DocumentClassificationResult>> getClassificationHistory(
            @PathVariable String documentId) {
        return ResponseEntity.ok(governanceService.getClassificationHistory(documentId));
    }

    /**
     * Approve the LLM's classification as-is.
     */
    @PostMapping("/{documentId}/approve")
    public ResponseEntity<DocumentModel> approve(
            @PathVariable String documentId,
            @AuthenticationPrincipal UserDetails user) {

        DocumentModel doc = documentService.getById(documentId);
        if (doc == null) return ResponseEntity.notFound().build();

        // Mark the classification as human-reviewed
        List<DocumentClassificationResult> results =
                governanceService.getClassificationHistory(documentId);
        if (!results.isEmpty()) {
            DocumentClassificationResult latest = results.getFirst();
            latest.setHumanReviewed(true);
            latest.setReviewedBy(user.getUsername());
            governanceService.saveClassificationResult(latest);

            // Record as a positive signal — the LLM got it right
            ClassificationCorrection correction = new ClassificationCorrection();
            correction.setDocumentId(documentId);
            correction.setOriginalCategoryId(latest.getCategoryId());
            correction.setOriginalCategoryName(latest.getCategoryName());
            correction.setOriginalSensitivity(latest.getSensitivityLabel());
            correction.setOriginalConfidence(latest.getConfidence());
            correction.setCorrectedCategoryId(latest.getCategoryId());
            correction.setCorrectedCategoryName(latest.getCategoryName());
            correction.setCorrectedSensitivity(latest.getSensitivityLabel());
            correction.setCorrectionType(CorrectionType.APPROVED_CORRECT);
            correction.setReason("Approved as correct by reviewer");
            correction.setMimeType(doc.getMimeType());
            correction.setCorrectedBy(user.getUsername());
            governanceService.saveCorrection(correction);
            bertCollector.collectCorrection(correction);
        }

        // Locally-uploaded documents go to triage for filing; external storage
        // documents (Google Drive, Gmail) are classified in-situ and skip triage.
        doc.setStatus("LOCAL".equals(doc.getStorageProvider())
                ? DocumentStatus.TRIAGE : DocumentStatus.GOVERNANCE_APPLIED);
        doc.setGovernanceAppliedAt(Instant.now());
        documentService.save(doc);

        auditEventRepository.save(new AuditEvent(
                documentId, "CLASSIFICATION_APPROVED", user.getUsername(), "USER",
                Map.of("action", "approve")
        ));
        platformAudit.emitUserAction(documentId, "CLASSIFICATION_APPROVED", "APPROVE_CLASSIFICATION",
                user.getUsername(), Outcome.SUCCESS,
                Map.of("action", "approve"), null);

        return ResponseEntity.ok(doc);
    }

    /**
     * Override the LLM's classification with a human decision.
     */
    @PostMapping("/{documentId}/override")
    public ResponseEntity<DocumentModel> override(
            @PathVariable String documentId,
            @RequestBody OverrideRequest request,
            @AuthenticationPrincipal UserDetails user) {

        DocumentModel doc = documentService.getById(documentId);
        if (doc == null) return ResponseEntity.notFound().build();

        // Get the original LLM classification for correction tracking
        List<DocumentClassificationResult> history =
                governanceService.getClassificationHistory(documentId);
        DocumentClassificationResult original = history.isEmpty() ? null : history.getFirst();

        // Create a new classification result with the human override
        DocumentClassificationResult override = new DocumentClassificationResult();
        override.setDocumentId(documentId);
        override.setCategoryId(request.categoryId());
        override.setCategoryName(request.categoryName());
        override.setSensitivityLabel(request.sensitivityLabel());
        override.setTags(request.tags());
        override.setConfidence(1.0);
        override.setReasoning("Human override by " + user.getUsername() + ": " + request.reason());
        override.setModelId("human");
        override.setHumanReviewed(true);
        override.setReviewedBy(user.getUsername());

        // ISO 15489 denormalisation — copy fields from the chosen category
        ClassificationCategory category = categoryRepo.findById(request.categoryId()).orElse(null);
        if (category != null) {
            override.setClassificationCode(category.getClassificationCode());
            override.setClassificationPath(category.getPath());
            override.setClassificationLevel(category.getLevel());
            override.setJurisdiction(category.getJurisdiction());
            override.setLegalCitation(category.getLegalCitation());
            override.setCategoryPersonalData(category.isPersonalDataFlag());
            override.setVitalRecord(category.isVitalRecordFlag());
            override.setTaxonomyVersion(category.getVersion());
            override.setRetentionTrigger(category.getRetentionTrigger());
            override.setRetentionPeriodText(category.getRetentionPeriodText());
            if (category.getRetentionScheduleId() != null) {
                override.setRetentionScheduleId(category.getRetentionScheduleId());
                RetentionSchedule sched = governanceService.getRetentionSchedule(category.getRetentionScheduleId());
                if (sched != null) {
                    override.setExpectedDispositionAction(sched.getDispositionAction());
                }
            }
        }
        governanceService.saveClassificationResult(override);

        // Record the correction for LLM feedback
        if (original != null) {
            boolean catChanged = !request.categoryId().equals(original.getCategoryId());
            boolean senChanged = request.sensitivityLabel() != original.getSensitivityLabel();

            ClassificationCorrection correction = new ClassificationCorrection();
            correction.setDocumentId(documentId);
            correction.setOriginalCategoryId(original.getCategoryId());
            correction.setOriginalCategoryName(original.getCategoryName());
            correction.setOriginalSensitivity(original.getSensitivityLabel());
            correction.setOriginalConfidence(original.getConfidence());
            correction.setCorrectedCategoryId(request.categoryId());
            correction.setCorrectedCategoryName(request.categoryName());
            correction.setCorrectedSensitivity(request.sensitivityLabel());
            correction.setCorrectionType(catChanged && senChanged ? CorrectionType.BOTH_CHANGED
                    : catChanged ? CorrectionType.CATEGORY_CHANGED
                    : CorrectionType.SENSITIVITY_CHANGED);
            correction.setReason(request.reason());
            correction.setMimeType(doc.getMimeType());
            correction.setKeywords(request.tags());
            correction.setCorrectedBy(user.getUsername());
            governanceService.saveCorrection(correction);
            bertCollector.collectCorrection(correction);

            // Wire feedback to the Classification Prompt block
            createBlockFeedback(documentId, BlockFeedback.FeedbackType.CORRECTION,
                    "Category: " + original.getCategoryName() + " → " + request.categoryName() +
                    (senChanged ? "; Sensitivity: " + original.getSensitivityLabel() + " → " + request.sensitivityLabel() : "") +
                    "; Reason: " + request.reason(),
                    original.getCategoryName(), request.categoryName(),
                    user.getUsername(), PipelineBlock.BlockType.PROMPT);
        }

        // Update the document with the override (including denormalised ISO 15489 fields)
        doc.setClassificationResultId(override.getId());
        doc.setCategoryId(request.categoryId());
        doc.setCategoryName(request.categoryName());
        doc.setSensitivityLabel(request.sensitivityLabel());
        doc.setTags(request.tags());
        doc.setClassificationCode(override.getClassificationCode());
        doc.setClassificationPath(override.getClassificationPath());
        doc.setClassificationLevel(override.getClassificationLevel());
        doc.setJurisdiction(override.getJurisdiction());
        doc.setLegalCitation(override.getLegalCitation());
        doc.setCategoryPersonalData(override.isCategoryPersonalData());
        doc.setVitalRecord(override.isVitalRecord());
        doc.setTaxonomyVersion(override.getTaxonomyVersion());
        doc.setRetentionTrigger(override.getRetentionTrigger());
        doc.setRetentionPeriodText(override.getRetentionPeriodText());
        doc.setExpectedDispositionAction(override.getExpectedDispositionAction());
        if (override.getRetentionScheduleId() != null) {
            doc.setRetentionScheduleId(override.getRetentionScheduleId());
        }
        doc.setStatus("LOCAL".equals(doc.getStorageProvider())
                ? DocumentStatus.TRIAGE : DocumentStatus.GOVERNANCE_APPLIED);
        doc.setGovernanceAppliedAt(Instant.now());
        documentService.save(doc);

        auditEventRepository.save(new AuditEvent(
                documentId, "CLASSIFICATION_OVERRIDDEN", user.getUsername(), "USER",
                Map.of(
                        "newCategory", request.categoryName(),
                        "newSensitivity", request.sensitivityLabel().name(),
                        "reason", request.reason()
                )
        ));
        platformAudit.emitUserAction(documentId, "CLASSIFICATION_OVERRIDDEN", "OVERRIDE_CATEGORY",
                user.getUsername(), Outcome.SUCCESS,
                Map.of("newCategory", request.categoryName(),
                        "newSensitivity", request.sensitivityLabel().name()),
                Map.of("reason", request.reason() == null ? "" : request.reason()));

        return ResponseEntity.ok(doc);
    }

    /**
     * Reject the document — flags it for further investigation.
     */
    @PostMapping("/{documentId}/reject")
    public ResponseEntity<DocumentModel> reject(
            @PathVariable String documentId,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserDetails user) {

        DocumentModel doc = documentService.getById(documentId);
        if (doc == null) return ResponseEntity.notFound().build();

        doc.setStatus(DocumentStatus.REVIEW_REQUIRED);
        documentService.save(doc);

        auditEventRepository.save(new AuditEvent(
                documentId, "CLASSIFICATION_REJECTED", user.getUsername(), "USER",
                Map.of("reason", body.getOrDefault("reason", ""))
        ));
        platformAudit.emitUserAction(documentId, "CLASSIFICATION_REJECTED", "REJECT_CLASSIFICATION",
                user.getUsername(), Outcome.SUCCESS, Map.of(),
                Map.of("reason", body.getOrDefault("reason", "")));

        return ResponseEntity.ok(doc);
    }

    /**
     * Report PII that the system missed. Creates a PII_FLAGGED correction
     * that feeds back to the LLM via the get_org_pii_patterns MCP tool.
     */
    @PostMapping("/{documentId}/report-pii")
    public ResponseEntity<ClassificationCorrection> reportPii(
            @PathVariable String documentId,
            @RequestBody PiiReportRequest request,
            @AuthenticationPrincipal UserDetails user) {

        DocumentModel doc = documentService.getById(documentId);
        if (doc == null) return ResponseEntity.notFound().build();

        ClassificationCorrection correction = new ClassificationCorrection();
        correction.setDocumentId(documentId);
        correction.setOriginalCategoryId(doc.getCategoryId());
        correction.setOriginalCategoryName(doc.getCategoryName());
        correction.setOriginalSensitivity(doc.getSensitivityLabel());
        correction.setCorrectedCategoryId(doc.getCategoryId());
        correction.setCorrectedCategoryName(doc.getCategoryName());
        correction.setCorrectedSensitivity(doc.getSensitivityLabel());
        correction.setCorrectionType(CorrectionType.PII_FLAGGED);
        correction.setReason("PII reported by " + user.getUsername());
        correction.setMimeType(doc.getMimeType());
        correction.setPiiCorrections(request.piiItems().stream()
                .map(p -> new ClassificationCorrection.PiiCorrection(p.type(), p.description(), p.context()))
                .toList());
        correction.setCorrectedBy(user.getUsername());

        ClassificationCorrection saved = governanceService.saveCorrection(correction);

        // Wire feedback to the PII Regex block
        for (var item : request.piiItems()) {
            createBlockFeedback(documentId, BlockFeedback.FeedbackType.MISSED,
                    "Missed PII: " + item.type() + " — " + item.description(),
                    null, item.type() + ": " + item.description(),
                    user.getUsername(), PipelineBlock.BlockType.REGEX_SET);
        }

        // Add the reported PII entities to the document's findings immediately
        // so they're visible without waiting for a re-scan
        var existingFindings = doc.getPiiFindings() != null
                ? new java.util.ArrayList<>(doc.getPiiFindings()) : new java.util.ArrayList<co.uk.wolfnotsheep.document.models.PiiEntity>();
        for (var item : request.piiItems()) {
            var entity = new co.uk.wolfnotsheep.document.models.PiiEntity(
                    item.type(), item.context(), item.context(),
                    0, 1.0, co.uk.wolfnotsheep.document.models.PiiEntity.DetectionMethod.LLM);
            entity.setVerified(true);
            entity.setVerifiedBy(user.getUsername());
            existingFindings.add(entity);
        }
        doc.setPiiFindings(existingFindings);
        long activeCount = existingFindings.stream()
                .filter(p -> !p.isDismissed()).count();
        doc.setPiiStatus(activeCount > 0 ? "DETECTED" : doc.getPiiStatus());
        doc.setPiiScannedAt(Instant.now());
        documentService.save(doc);

        auditEventRepository.save(new AuditEvent(
                documentId, "PII_REPORTED", user.getUsername(), "USER",
                Map.of("piiCount", String.valueOf(request.piiItems().size()))
        ));
        platformAudit.emitUserAction(documentId, "PII_REPORTED", "REPORT_PII",
                user.getUsername(), Outcome.SUCCESS,
                Map.of("piiCount", request.piiItems().size()), null);

        return ResponseEntity.ok(saved);
    }

    @GetMapping("/corrections")
    public ResponseEntity<List<ClassificationCorrection>> getRecentCorrections() {
        return ResponseEntity.ok(governanceService.getRecentCorrections(20));
    }

    @GetMapping("/low-confidence")
    public ResponseEntity<List<DocumentClassificationResult>> getLowConfidence(
            @RequestParam(defaultValue = "0.7") double threshold) {
        return ResponseEntity.ok(governanceService.getLowConfidenceResults(threshold));
    }

    public record OverrideRequest(
            String categoryId,
            String categoryName,
            SensitivityLabel sensitivityLabel,
            List<String> tags,
            String reason
    ) {}

    public record PiiReportRequest(List<PiiItem> piiItems) {}

    public record PiiItem(String type, String description, String context) {}

    // ── Block Feedback Helper ────────────────────────────

    private void createBlockFeedback(String documentId, BlockFeedback.FeedbackType type,
                                      String details, String originalValue, String correctedValue,
                                      String userEmail, PipelineBlock.BlockType blockType) {
        // Find the block of this type
        List<PipelineBlock> blocks = blockRepo.findByTypeAndActiveTrueOrderByNameAsc(blockType);
        if (blocks.isEmpty()) return;

        PipelineBlock block = blocks.getFirst();
        BlockFeedback feedback = new BlockFeedback();
        feedback.setBlockId(block.getId());
        feedback.setBlockVersion(block.getActiveVersion());
        feedback.setDocumentId(documentId);
        feedback.setUserEmail(userEmail);
        feedback.setType(type);
        feedback.setDetails(details);
        feedback.setOriginalValue(originalValue);
        feedback.setCorrectedValue(correctedValue);
        blockFeedbackRepo.save(feedback);

        // Update feedback count
        block.setFeedbackCount(blockFeedbackRepo.countByBlockId(block.getId()));
        block.setCorrectionsReceived(block.getCorrectionsReceived() + 1);
        blockRepo.save(block);
    }
}
