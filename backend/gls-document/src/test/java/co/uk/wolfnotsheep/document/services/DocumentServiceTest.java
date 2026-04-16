package co.uk.wolfnotsheep.document.services;

import co.uk.wolfnotsheep.document.models.AuditEvent;
import co.uk.wolfnotsheep.document.models.DocumentModel;
import co.uk.wolfnotsheep.document.models.DocumentStatus;
import co.uk.wolfnotsheep.document.repositories.AuditEventRepository;
import co.uk.wolfnotsheep.document.repositories.DocumentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private AuditEventRepository auditEventRepository;

    @Mock
    private ObjectStorageService objectStorage;

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private PipelineStatusNotifier statusNotifier;

    @InjectMocks
    private DocumentService documentService;

    // ── updateStatus ────────────────────────────────────────

    @Test
    void updateStatus_changesStatusAndSaves() {
        DocumentModel doc = buildDoc("doc-1", DocumentStatus.UPLOADED);
        when(documentRepository.findById("doc-1")).thenReturn(Optional.of(doc));
        when(documentRepository.save(any(DocumentModel.class))).thenAnswer(inv -> inv.getArgument(0));

        DocumentModel result = documentService.updateStatus("doc-1", DocumentStatus.PROCESSING, "user-1");

        assertThat(result.getStatus()).isEqualTo(DocumentStatus.PROCESSING);
        assertThat(result.getUpdatedAt()).isNotNull();
    }

    @Test
    void updateStatus_setsProcessedAtWhenProcessed() {
        DocumentModel doc = buildDoc("doc-1", DocumentStatus.PROCESSING);
        when(documentRepository.findById("doc-1")).thenReturn(Optional.of(doc));
        when(documentRepository.save(any(DocumentModel.class))).thenAnswer(inv -> inv.getArgument(0));

        DocumentModel result = documentService.updateStatus("doc-1", DocumentStatus.PROCESSED, "user-1");

        assertThat(result.getProcessedAt()).isNotNull();
    }

    @Test
    void updateStatus_setsClassifiedAtWhenClassified() {
        DocumentModel doc = buildDoc("doc-1", DocumentStatus.CLASSIFYING);
        when(documentRepository.findById("doc-1")).thenReturn(Optional.of(doc));
        when(documentRepository.save(any(DocumentModel.class))).thenAnswer(inv -> inv.getArgument(0));

        DocumentModel result = documentService.updateStatus("doc-1", DocumentStatus.CLASSIFIED, "user-1");

        assertThat(result.getClassifiedAt()).isNotNull();
    }

    @Test
    void updateStatus_setsGovernanceAppliedAtForGovernanceApplied() {
        DocumentModel doc = buildDoc("doc-1", DocumentStatus.CLASSIFIED);
        when(documentRepository.findById("doc-1")).thenReturn(Optional.of(doc));
        when(documentRepository.save(any(DocumentModel.class))).thenAnswer(inv -> inv.getArgument(0));

        DocumentModel result = documentService.updateStatus("doc-1", DocumentStatus.GOVERNANCE_APPLIED, "user-1");

        assertThat(result.getGovernanceAppliedAt()).isNotNull();
    }

    @Test
    void updateStatus_setsGovernanceAppliedAtForInbox() {
        DocumentModel doc = buildDoc("doc-1", DocumentStatus.CLASSIFIED);
        when(documentRepository.findById("doc-1")).thenReturn(Optional.of(doc));
        when(documentRepository.save(any(DocumentModel.class))).thenAnswer(inv -> inv.getArgument(0));

        DocumentModel result = documentService.updateStatus("doc-1", DocumentStatus.INBOX, "user-1");

        assertThat(result.getGovernanceAppliedAt()).isNotNull();
    }

    @Test
    void updateStatus_createsAuditEvent() {
        DocumentModel doc = buildDoc("doc-1", DocumentStatus.UPLOADED);
        when(documentRepository.findById("doc-1")).thenReturn(Optional.of(doc));
        when(documentRepository.save(any(DocumentModel.class))).thenAnswer(inv -> inv.getArgument(0));

        documentService.updateStatus("doc-1", DocumentStatus.PROCESSING, "user-1");

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventRepository).save(captor.capture());
        AuditEvent event = captor.getValue();
        assertThat(event.getDocumentId()).isEqualTo("doc-1");
        assertThat(event.getAction()).isEqualTo("STATUS_CHANGED");
        assertThat(event.getPerformedBy()).isEqualTo("user-1");
        assertThat(event.getDetails()).containsEntry("from", "UPLOADED").containsEntry("to", "PROCESSING");
    }

    @Test
    void updateStatus_notifiesStatusChange() {
        DocumentModel doc = buildDoc("doc-1", DocumentStatus.UPLOADED);
        doc.setOriginalFileName("report.pdf");
        when(documentRepository.findById("doc-1")).thenReturn(Optional.of(doc));
        when(documentRepository.save(any(DocumentModel.class))).thenAnswer(inv -> inv.getArgument(0));

        documentService.updateStatus("doc-1", DocumentStatus.PROCESSING, "user-1");

        verify(statusNotifier).notifyStatusChange("doc-1", "PROCESSING", "report.pdf");
    }

    @Test
    void updateStatus_throwsWhenDocumentNotFound() {
        when(documentRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> documentService.updateStatus("missing", DocumentStatus.PROCESSING, "user-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Document not found");
    }

    // ── setError ────────────────────────────────────────────

    @Test
    void setError_setsFailedStatusAndErrorFields() {
        DocumentModel doc = buildDoc("doc-1", DocumentStatus.PROCESSING);
        when(documentRepository.findById("doc-1")).thenReturn(Optional.of(doc));
        when(documentRepository.save(any(DocumentModel.class))).thenAnswer(inv -> inv.getArgument(0));

        DocumentModel result = documentService.setError("doc-1", DocumentStatus.PROCESSING_FAILED, "EXTRACTION", "Tika failed");

        assertThat(result.getStatus()).isEqualTo(DocumentStatus.PROCESSING_FAILED);
        assertThat(result.getLastError()).isEqualTo("Tika failed");
        assertThat(result.getLastErrorStage()).isEqualTo("EXTRACTION");
        assertThat(result.getFailedAt()).isNotNull();
        assertThat(result.getUpdatedAt()).isNotNull();
    }

    @Test
    void setError_createsAuditEventWithErrorDetails() {
        DocumentModel doc = buildDoc("doc-1", DocumentStatus.CLASSIFYING);
        when(documentRepository.findById("doc-1")).thenReturn(Optional.of(doc));
        when(documentRepository.save(any(DocumentModel.class))).thenAnswer(inv -> inv.getArgument(0));

        documentService.setError("doc-1", DocumentStatus.CLASSIFICATION_FAILED, "LLM", "Timeout");

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventRepository).save(captor.capture());
        AuditEvent event = captor.getValue();
        assertThat(event.getAction()).isEqualTo("PROCESSING_FAILED");
        assertThat(event.getDetails())
                .containsEntry("stage", "LLM")
                .containsEntry("error", "Timeout")
                .containsEntry("from", "CLASSIFYING")
                .containsEntry("to", "CLASSIFICATION_FAILED");
    }

    @Test
    void setError_notifiesStatusChange() {
        DocumentModel doc = buildDoc("doc-1", DocumentStatus.PROCESSING);
        doc.setOriginalFileName("invoice.pdf");
        when(documentRepository.findById("doc-1")).thenReturn(Optional.of(doc));
        when(documentRepository.save(any(DocumentModel.class))).thenAnswer(inv -> inv.getArgument(0));

        documentService.setError("doc-1", DocumentStatus.PROCESSING_FAILED, "EXTRACTION", "error");

        verify(statusNotifier).notifyStatusChange("doc-1", "PROCESSING_FAILED", "invoice.pdf");
    }

    @Test
    void setError_handlesNullErrorMessage() {
        DocumentModel doc = buildDoc("doc-1", DocumentStatus.PROCESSING);
        when(documentRepository.findById("doc-1")).thenReturn(Optional.of(doc));
        when(documentRepository.save(any(DocumentModel.class))).thenAnswer(inv -> inv.getArgument(0));

        documentService.setError("doc-1", DocumentStatus.PROCESSING_FAILED, "EXTRACTION", null);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventRepository).save(captor.capture());
        assertThat(captor.getValue().getDetails()).containsEntry("error", "unknown");
    }

    @Test
    void setError_throwsWhenDocumentNotFound() {
        when(documentRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> documentService.setError("missing", DocumentStatus.PROCESSING_FAILED, "EXTRACTION", "err"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Document not found");
    }

    // ── getById ─────────────────────────────────────────────

    @Test
    void getById_returnsDocumentWhenFound() {
        DocumentModel doc = buildDoc("doc-1", DocumentStatus.UPLOADED);
        when(documentRepository.findById("doc-1")).thenReturn(Optional.of(doc));

        DocumentModel result = documentService.getById("doc-1");

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo("doc-1");
    }

    @Test
    void getById_returnsNullWhenNotFound() {
        when(documentRepository.findById("missing")).thenReturn(Optional.empty());

        DocumentModel result = documentService.getById("missing");

        assertThat(result).isNull();
    }

    // ── clearErrorForReprocess ──────────────────────────────

    @Test
    void clearErrorForReprocess_resetsErrorStateAndSetsUploaded() {
        DocumentModel doc = buildDoc("doc-1", DocumentStatus.PROCESSING_FAILED);
        doc.setLastError("Tika failed");
        doc.setLastErrorStage("EXTRACTION");
        doc.setFailedAt(Instant.now());
        doc.setCancelledAt(Instant.now());
        doc.setProcessedAt(Instant.now());
        doc.setClassifiedAt(Instant.now());
        doc.setGovernanceAppliedAt(Instant.now());
        doc.setPiiScannedAt(Instant.now());
        doc.setRetryCount(2);
        when(documentRepository.findById("doc-1")).thenReturn(Optional.of(doc));
        when(documentRepository.save(any(DocumentModel.class))).thenAnswer(inv -> inv.getArgument(0));

        DocumentModel result = documentService.clearErrorForReprocess("doc-1");

        assertThat(result.getStatus()).isEqualTo(DocumentStatus.UPLOADED);
        assertThat(result.getLastError()).isNull();
        assertThat(result.getLastErrorStage()).isNull();
        assertThat(result.getFailedAt()).isNull();
        assertThat(result.getCancelledAt()).isNull();
        assertThat(result.getProcessedAt()).isNull();
        assertThat(result.getClassifiedAt()).isNull();
        assertThat(result.getGovernanceAppliedAt()).isNull();
        assertThat(result.getPiiScannedAt()).isNull();
        assertThat(result.getRetryCount()).isEqualTo(3);
        assertThat(result.getUpdatedAt()).isNotNull();
    }

    @Test
    void clearErrorForReprocess_throwsWhenDocumentNotFound() {
        when(documentRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> documentService.clearErrorForReprocess("missing"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Document not found");
    }

    // ── helpers ─────────────────────────────────────────────

    private DocumentModel buildDoc(String id, DocumentStatus status) {
        DocumentModel doc = new DocumentModel();
        doc.setId(id);
        doc.setStatus(status);
        doc.setCreatedAt(Instant.now());
        doc.setUpdatedAt(Instant.now());
        return doc;
    }
}
