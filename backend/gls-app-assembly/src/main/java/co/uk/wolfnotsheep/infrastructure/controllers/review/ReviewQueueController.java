package co.uk.wolfnotsheep.infrastructure.controllers.review;

import co.uk.wolfnotsheep.document.models.AuditEvent;
import co.uk.wolfnotsheep.document.models.DocumentModel;
import co.uk.wolfnotsheep.document.models.DocumentStatus;
import co.uk.wolfnotsheep.document.repositories.AuditEventRepository;
import co.uk.wolfnotsheep.document.services.DocumentService;
import co.uk.wolfnotsheep.governance.models.DocumentClassificationResult;
import co.uk.wolfnotsheep.governance.models.SensitivityLabel;
import co.uk.wolfnotsheep.governance.services.GovernanceService;
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

    public ReviewQueueController(DocumentService documentService,
                                 GovernanceService governanceService,
                                 AuditEventRepository auditEventRepository) {
        this.documentService = documentService;
        this.governanceService = governanceService;
        this.auditEventRepository = auditEventRepository;
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
        }

        // Advance status
        doc.setStatus(DocumentStatus.GOVERNANCE_APPLIED);
        doc.setGovernanceAppliedAt(Instant.now());
        documentService.save(doc);

        auditEventRepository.save(new AuditEvent(
                documentId, "CLASSIFICATION_APPROVED", user.getUsername(), "USER",
                Map.of("action", "approve")
        ));

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
        governanceService.saveClassificationResult(override);

        // Update the document with the override
        doc.setClassificationResultId(override.getId());
        doc.setCategoryId(request.categoryId());
        doc.setCategoryName(request.categoryName());
        doc.setSensitivityLabel(request.sensitivityLabel());
        doc.setTags(request.tags());
        doc.setStatus(DocumentStatus.GOVERNANCE_APPLIED);
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

        return ResponseEntity.ok(doc);
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
}
