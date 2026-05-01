package co.uk.wolfnotsheep.enforcement.web;

import co.uk.wolfnotsheep.document.events.DocumentClassifiedEvent;
import co.uk.wolfnotsheep.document.models.DocumentModel;
import co.uk.wolfnotsheep.document.services.DocumentService;
import co.uk.wolfnotsheep.enforcement.api.EnforceApi;
import co.uk.wolfnotsheep.enforcement.jobs.JobAcquisition;
import co.uk.wolfnotsheep.enforcement.jobs.JobRecord;
import co.uk.wolfnotsheep.enforcement.jobs.JobState;
import co.uk.wolfnotsheep.enforcement.jobs.JobStore;
import co.uk.wolfnotsheep.enforcement.model.AppliedSummary;
import co.uk.wolfnotsheep.enforcement.model.ClassificationEvent;
import co.uk.wolfnotsheep.enforcement.model.EnforceRequest;
import co.uk.wolfnotsheep.enforcement.model.EnforceResponse;
import co.uk.wolfnotsheep.enforcement.model.JobAccepted;
import co.uk.wolfnotsheep.enforcement.services.EnforcementService;
import co.uk.wolfnotsheep.governance.services.GovernanceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EnforceControllerTest {

    private EnforcementService enforcementService;
    private DocumentService documentService;
    private GovernanceService governanceService;
    private JobStore jobs;
    private ObjectMapper mapper;
    private AsyncDispatcher asyncDispatcher;
    private EnforceController controller;

    @BeforeEach
    void setUp() {
        enforcementService = mock(EnforcementService.class);
        documentService = mock(DocumentService.class);
        governanceService = mock(GovernanceService.class);
        jobs = mock(JobStore.class);
        when(jobs.tryAcquire(anyString())).thenReturn(JobAcquisition.acquired());
        mapper = new ObjectMapper().findAndRegisterModules();
        asyncDispatcher = mock(AsyncDispatcher.class);
        controller = new EnforceController(
                enforcementService, documentService, governanceService,
                jobs, mapper, asyncDispatcher);
    }

    @Test
    void controller_implements_generated_api() {
        assertThat(EnforceApi.class.isAssignableFrom(EnforceController.class)).isTrue();
    }

    @Test
    void happy_path_returns_200_with_applied_summary_and_caches() {
        DocumentModel before = new DocumentModel();
        before.setId("doc-1");
        before.setStorageTierId("tier-hot");
        when(documentService.getById("doc-1")).thenReturn(before);

        DocumentModel after = new DocumentModel();
        after.setId("doc-1");
        after.setStorageTierId("tier-cold");
        after.setAppliedPolicyIds(List.of("policy-uk-gdpr"));
        when(enforcementService.enforce(any(DocumentClassifiedEvent.class))).thenReturn(after);

        ResponseEntity<EnforceResponse> resp = controller.enforce(
                validTraceparent(), buildRequest("nr-1", "doc-1"), null, null);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        EnforceResponse body = resp.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getNodeRunId()).isEqualTo("nr-1");
        assertThat(body.getDocumentId()).isEqualTo("doc-1");
        AppliedSummary applied = body.getApplied();
        assertThat(applied.getStorageTierBefore()).isEqualTo("tier-hot");
        assertThat(applied.getStorageTierAfter()).isEqualTo("tier-cold");
        assertThat(applied.getStorageMigrated()).isTrue();
        assertThat(applied.getAppliedPolicyIds()).containsExactly("policy-uk-gdpr");

        verify(jobs, times(1)).markRunning("nr-1");
        verify(jobs, times(1)).markCompleted(eq("nr-1"), anyString());
    }

    @Test
    void missing_document_throws_DocumentNotFoundException_and_marks_failed() {
        when(documentService.getById("doc-missing")).thenReturn(null);

        assertThatThrownBy(() -> controller.enforce(
                validTraceparent(), buildRequest("nr-2", "doc-missing"), null, null))
                .isInstanceOf(DocumentNotFoundException.class);

        verify(jobs, times(1)).markFailed(eq("nr-2"), eq("DOCUMENT_NOT_FOUND"), anyString());
        verify(enforcementService, never()).enforce(any());
    }

    @Test
    void in_flight_idempotency_short_circuits_sync_with_409() {
        when(jobs.tryAcquire("nr-busy")).thenReturn(
                JobAcquisition.running(pendingRow("nr-busy")));

        assertThatThrownBy(() -> controller.enforce(
                validTraceparent(), buildRequest("nr-busy", "doc-1"), null, null))
                .isInstanceOf(JobInFlightException.class);

        verify(documentService, never()).getById(any());
        verify(enforcementService, never()).enforce(any());
    }

    @Test
    void completed_idempotency_returns_cached_200() throws Exception {
        EnforceResponse cached = new EnforceResponse();
        cached.setNodeRunId("nr-cached");
        cached.setDocumentId("doc-1");
        cached.setApplied(new AppliedSummary());
        cached.setDurationMs(10);
        String json = mapper.writeValueAsString(cached);
        Instant now = Instant.now();
        when(jobs.tryAcquire("nr-cached")).thenReturn(JobAcquisition.completed(
                new JobRecord("nr-cached", JobState.COMPLETED, now, now, now, json, null, null,
                        now.plusSeconds(60))));

        ResponseEntity<EnforceResponse> resp = controller.enforce(
                validTraceparent(), buildRequest("nr-cached", "doc-1"), null, null);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().getNodeRunId()).isEqualTo("nr-cached");
        verify(documentService, never()).getById(any());
        verify(enforcementService, never()).enforce(any());
    }

    @Test
    void async_path_returns_202_with_location_header() {
        ResponseEntity<?> resp = controller.enforce(
                validTraceparent(), buildRequest("nr-async", "doc-1"), null, "respond-async");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(resp.getHeaders().getFirst(HttpHeaders.LOCATION))
                .isEqualTo("/v1/jobs/nr-async");
        Object body = resp.getBody();
        assertThat(body).isInstanceOf(JobAccepted.class);
        assertThat(((JobAccepted) body).getNodeRunId()).isEqualTo("nr-async");
        verify(asyncDispatcher, times(1)).dispatch(any(EnforceRequest.class));
    }

    @Test
    void invalid_input_throws_EnforcementInvalidInputException_and_marks_failed() {
        // Missing classificationResultId
        ClassificationEvent c = new ClassificationEvent();
        c.setDocumentId("doc-1");
        c.setCategoryId("cat-1");
        c.setClassifiedAt(OffsetDateTime.now(ZoneOffset.UTC));
        EnforceRequest req = new EnforceRequest("nr-bad", c);

        assertThatThrownBy(() -> controller.enforce(validTraceparent(), req, null, null))
                .isInstanceOf(EnforcementInvalidInputException.class);

        verify(jobs, times(1)).markFailed(eq("nr-bad"), eq("ENFORCEMENT_INVALID_INPUT"), anyString());
    }

    @Test
    void enforcement_service_failure_propagates_and_marks_failed() {
        DocumentModel before = new DocumentModel();
        before.setId("doc-1");
        before.setStorageTierId("tier-hot");
        when(documentService.getById("doc-1")).thenReturn(before);
        when(enforcementService.enforce(any())).thenThrow(new IllegalStateException("rabbit down"));

        assertThatThrownBy(() -> controller.enforce(
                validTraceparent(), buildRequest("nr-boom", "doc-1"), null, null))
                .isInstanceOf(IllegalStateException.class);

        verify(jobs, times(1)).markFailed(eq("nr-boom"), eq("ENFORCEMENT_UNEXPECTED"), anyString());
    }

    private static EnforceRequest buildRequest(String nodeRunId, String docId) {
        ClassificationEvent c = new ClassificationEvent();
        c.setDocumentId(docId);
        c.setClassificationResultId("cr-1");
        c.setCategoryId("cat-1");
        c.setCategoryName("HR > Contracts");
        c.setSensitivityLabel(ClassificationEvent.SensitivityLabelEnum.INTERNAL);
        c.setConfidence(0.9f);
        c.setRequiresHumanReview(false);
        c.setClassifiedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return new EnforceRequest(nodeRunId, c);
    }

    private static String validTraceparent() {
        return "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01";
    }

    private static JobRecord pendingRow(String nodeRunId) {
        Instant now = Instant.now();
        return new JobRecord(nodeRunId, JobState.PENDING, now, null, null, null, null, null,
                now.plusSeconds(60));
    }
}
