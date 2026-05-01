package co.uk.wolfnotsheep.enforcement.services;

import co.uk.wolfnotsheep.document.events.DocumentClassifiedEvent;
import co.uk.wolfnotsheep.document.models.AuditEvent;
import co.uk.wolfnotsheep.document.models.DocumentModel;
import co.uk.wolfnotsheep.document.models.PiiEntity;
import co.uk.wolfnotsheep.document.models.PiiEntity.DetectionMethod;
import co.uk.wolfnotsheep.document.repositories.AuditEventRepository;
import co.uk.wolfnotsheep.document.repositories.SystemErrorRepository;
import co.uk.wolfnotsheep.document.services.DocumentService;
import co.uk.wolfnotsheep.document.services.ObjectStorageService;
import co.uk.wolfnotsheep.governance.models.DocumentClassificationResult;
import co.uk.wolfnotsheep.governance.models.PipelineBlock;
import co.uk.wolfnotsheep.governance.models.RetentionSchedule;
import co.uk.wolfnotsheep.governance.models.SensitivityLabel;
import co.uk.wolfnotsheep.governance.models.StorageTier;
import co.uk.wolfnotsheep.governance.repositories.PipelineBlockRepository;
import co.uk.wolfnotsheep.governance.services.GovernanceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EnforcementServiceTest {

    @Mock private DocumentService documentService;
    @Mock private GovernanceService governanceService;
    @Mock private ObjectStorageService objectStorage;
    @Mock private AuditEventRepository auditEventRepository;
    @Mock private PipelineBlockRepository blockRepo;
    @Mock private SystemErrorRepository systemErrorRepo;
    @Mock private co.uk.wolfnotsheep.enforcement.audit.EnforcementAuditEmitter platformAudit;

    @InjectMocks
    private EnforcementService enforcementService;

    // ── enforce(): happy path ─────────────────────────────────────────

    @Test
    void enforce_appliesGovernanceAndSetsRetentionAndStorageTier() {
        String docId = "doc-1";
        DocumentModel doc = buildDocument(docId, SensitivityLabel.INTERNAL);
        doc.setStorageBucket("igc-default");
        doc.setStorageKey("files/doc-1.pdf");
        doc.setStorageTierId("old-tier");

        DocumentClassifiedEvent event = new DocumentClassifiedEvent(
                docId, "cr-1", "cat-1", "HR > Contracts",
                SensitivityLabel.INTERNAL, List.of("contract", "hr"),
                List.of("policy-1"), "retention-1",
                0.92, false, Instant.now());

        when(documentService.getById(docId)).thenReturn(doc);
        when(blockRepo.findByTypeAndActiveTrueOrderByNameAsc(PipelineBlock.BlockType.ENFORCER))
                .thenReturn(Collections.emptyList());

        // Classification history with summary
        DocumentClassificationResult classResult = new DocumentClassificationResult();
        classResult.setSummary("An employment contract for a new hire.");
        classResult.setExtractedMetadata(Map.of("employee_name", "Jane Doe"));
        when(governanceService.getClassificationHistory(docId)).thenReturn(List.of(classResult));

        // Retention schedule
        RetentionSchedule retention = new RetentionSchedule();
        retention.setRetentionDays(365);
        when(governanceService.getRetentionSchedule("retention-1")).thenReturn(retention);

        // Storage tier
        StorageTier tier = new StorageTier();
        tier.setId("tier-standard");
        tier.setName("Standard");
        tier.setCostPerGbMonth(0.02);
        when(governanceService.getStorageTiersForSensitivity(SensitivityLabel.INTERNAL))
                .thenReturn(List.of(tier));
        when(objectStorage.exists("igc-standard", "files/doc-1.pdf")).thenReturn(true);

        DocumentModel result = enforcementService.enforce(event);

        assertThat(result).isNotNull();
        assertThat(result.getClassificationResultId()).isEqualTo("cr-1");
        assertThat(result.getCategoryName()).isEqualTo("HR > Contracts");
        assertThat(result.getSensitivityLabel()).isEqualTo(SensitivityLabel.INTERNAL);
        assertThat(result.getTags()).containsExactly("contract", "hr");
        assertThat(result.getSummary()).isEqualTo("An employment contract for a new hire.");
        assertThat(result.getExtractedMetadata()).containsEntry("employee_name", "Jane Doe");
        assertThat(result.getRetentionScheduleId()).isEqualTo("retention-1");
        assertThat(result.getRetentionExpiresAt()).isNotNull();
        assertThat(result.getGovernanceAppliedAt()).isNotNull();

        // Storage migration happened
        verify(objectStorage).copy("igc-default", "files/doc-1.pdf", "igc-standard", "files/doc-1.pdf");
        assertThat(result.getStorageBucket()).isEqualTo("igc-standard");
        assertThat(result.getStorageTierId()).isEqualTo("tier-standard");

        // Document saved and audit event recorded
        verify(documentService, atLeastOnce()).save(doc);
        verify(auditEventRepository, atLeastOnce()).save(any(AuditEvent.class));
    }

    // ── enforce(): missing document ───────────────────────────────────

    @Test
    void enforce_returnsNullWhenDocumentNotFound() {
        DocumentClassifiedEvent event = new DocumentClassifiedEvent(
                "missing-doc", "cr-1", "cat-1", "Finance",
                SensitivityLabel.PUBLIC, List.of(), List.of(),
                null, 0.85, false, Instant.now());

        when(documentService.getById("missing-doc")).thenReturn(null);

        DocumentModel result = enforcementService.enforce(event);

        assertThat(result).isNull();
        verify(documentService, never()).save(any());
        verify(auditEventRepository, never()).save(any());
    }

    // ── enforce(): PII escalation ─────────────────────────────────────

    @Test
    void enforce_escalatesToConfidentialWhenHighRiskPiiDetected() {
        String docId = "doc-pii-1";
        DocumentModel doc = buildDocument(docId, SensitivityLabel.INTERNAL);
        doc.setStorageTierId("tier-1");
        doc.setStorageBucket("igc-default");
        doc.setStorageKey("files/pii.pdf");

        // One high-risk PII entity (NATIONAL_INSURANCE is in the default high-risk list)
        PiiEntity niPii = new PiiEntity("NATIONAL_INSURANCE", "AB123456C", "***", 10, 0.99, DetectionMethod.PATTERN);
        doc.setPiiFindings(List.of(niPii));

        DocumentClassifiedEvent event = new DocumentClassifiedEvent(
                docId, "cr-2", "cat-1", "HR > Payroll",
                SensitivityLabel.INTERNAL, List.of(), List.of(),
                null, 0.88, false, Instant.now());

        when(documentService.getById(docId)).thenReturn(doc);
        when(blockRepo.findByTypeAndActiveTrueOrderByNameAsc(PipelineBlock.BlockType.ENFORCER))
                .thenReturn(Collections.emptyList());
        when(governanceService.getClassificationHistory(docId)).thenReturn(Collections.emptyList());
        when(governanceService.getStorageTiersForSensitivity(SensitivityLabel.CONFIDENTIAL))
                .thenReturn(Collections.emptyList()); // no tier migration needed for this test

        DocumentModel result = enforcementService.enforce(event);

        assertThat(result).isNotNull();
        assertThat(result.getSensitivityLabel()).isEqualTo(SensitivityLabel.CONFIDENTIAL);

        // Verify escalation audit event was recorded
        ArgumentCaptor<AuditEvent> auditCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventRepository, atLeastOnce()).save(auditCaptor.capture());
        boolean hasEscalationEvent = auditCaptor.getAllValues().stream()
                .anyMatch(e -> "PII_SENSITIVITY_ESCALATED".equals(e.getAction()));
        assertThat(hasEscalationEvent).isTrue();
    }

    @Test
    void enforce_escalatesToRestrictedWhenPiiCountExceedsThreshold() {
        String docId = "doc-pii-2";
        DocumentModel doc = buildDocument(docId, SensitivityLabel.INTERNAL);
        doc.setStorageTierId("tier-1");
        doc.setStorageBucket("igc-default");
        doc.setStorageKey("files/heavy-pii.pdf");

        // 5 non-dismissed PII findings (default threshold is 5) -- none high-risk
        List<PiiEntity> findings = new java.util.ArrayList<>();
        for (int i = 0; i < 5; i++) {
            findings.add(new PiiEntity("EMAIL", "user" + i + "@example.com", "***", i * 20, 0.95, DetectionMethod.PATTERN));
        }
        doc.setPiiFindings(findings);

        DocumentClassifiedEvent event = new DocumentClassifiedEvent(
                docId, "cr-3", "cat-1", "General",
                SensitivityLabel.INTERNAL, List.of(), List.of(),
                null, 0.80, false, Instant.now());

        when(documentService.getById(docId)).thenReturn(doc);
        when(blockRepo.findByTypeAndActiveTrueOrderByNameAsc(PipelineBlock.BlockType.ENFORCER))
                .thenReturn(Collections.emptyList());
        when(governanceService.getClassificationHistory(docId)).thenReturn(Collections.emptyList());
        when(governanceService.getStorageTiersForSensitivity(SensitivityLabel.RESTRICTED))
                .thenReturn(Collections.emptyList());

        DocumentModel result = enforcementService.enforce(event);

        assertThat(result).isNotNull();
        assertThat(result.getSensitivityLabel()).isEqualTo(SensitivityLabel.RESTRICTED);
    }

    @Test
    void enforce_doesNotEscalateWhenPiiEntitiesAreDismissed() {
        String docId = "doc-pii-3";
        DocumentModel doc = buildDocument(docId, SensitivityLabel.PUBLIC);
        doc.setStorageTierId("tier-1");
        doc.setStorageBucket("igc-default");
        doc.setStorageKey("files/dismissed.pdf");

        // High-risk PII but dismissed
        PiiEntity dismissedPii = new PiiEntity("NATIONAL_INSURANCE", "AB123456C", "***", 10, 0.99, DetectionMethod.PATTERN);
        dismissedPii.setDismissed(true);
        dismissedPii.setDismissedBy("admin");
        doc.setPiiFindings(List.of(dismissedPii));

        DocumentClassifiedEvent event = new DocumentClassifiedEvent(
                docId, "cr-4", "cat-1", "General",
                SensitivityLabel.PUBLIC, List.of(), List.of(),
                null, 0.90, false, Instant.now());

        when(documentService.getById(docId)).thenReturn(doc);
        when(blockRepo.findByTypeAndActiveTrueOrderByNameAsc(PipelineBlock.BlockType.ENFORCER))
                .thenReturn(Collections.emptyList());
        when(governanceService.getClassificationHistory(docId)).thenReturn(Collections.emptyList());
        when(governanceService.getStorageTiersForSensitivity(SensitivityLabel.PUBLIC))
                .thenReturn(Collections.emptyList());

        DocumentModel result = enforcementService.enforce(event);

        assertThat(result).isNotNull();
        // Sensitivity should remain PUBLIC because the PII was dismissed
        assertThat(result.getSensitivityLabel()).isEqualTo(SensitivityLabel.PUBLIC);
    }

    // ── helpers ────────────────────────────────────────────────────────

    private DocumentModel buildDocument(String id, SensitivityLabel sensitivity) {
        DocumentModel doc = new DocumentModel();
        doc.setId(id);
        doc.setSensitivityLabel(sensitivity);
        return doc;
    }
}
