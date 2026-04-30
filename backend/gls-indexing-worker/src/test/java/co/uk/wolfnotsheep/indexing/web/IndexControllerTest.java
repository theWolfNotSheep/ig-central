package co.uk.wolfnotsheep.indexing.web;

import co.uk.wolfnotsheep.indexing.api.IndexApi;
import co.uk.wolfnotsheep.indexing.jobs.JobAcquisition;
import co.uk.wolfnotsheep.indexing.jobs.JobRecord;
import co.uk.wolfnotsheep.indexing.jobs.JobState;
import co.uk.wolfnotsheep.indexing.jobs.JobStore;
import co.uk.wolfnotsheep.indexing.model.DeleteResponse;
import co.uk.wolfnotsheep.indexing.model.IndexResponse;
import co.uk.wolfnotsheep.indexing.service.DocumentNotFoundException;
import co.uk.wolfnotsheep.indexing.service.IndexBackendUnavailableException;
import co.uk.wolfnotsheep.indexing.service.IndexingService;
import co.uk.wolfnotsheep.indexing.service.MappingConflictException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;

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

class IndexControllerTest {

    private IndexingService indexingService;
    private JobStore jobs;
    private ObjectMapper mapper;
    private IndexController controller;

    @BeforeEach
    void setUp() {
        indexingService = mock(IndexingService.class);
        jobs = mock(JobStore.class);
        when(jobs.tryAcquire(anyString())).thenReturn(JobAcquisition.acquired());
        mapper = new ObjectMapper().findAndRegisterModules();
        controller = new IndexController(indexingService, jobs, mapper);
    }

    @Test
    void controller_implements_generated_api() {
        assertThat(IndexApi.class.isAssignableFrom(IndexController.class)).isTrue();
    }

    @Test
    void happy_path_returns_200_with_index_response_and_caches() {
        when(indexingService.indexDocument("doc-1"))
                .thenReturn(new IndexingService.IndexOutcome("doc-1", "ig_central_documents", 7L));

        ResponseEntity<IndexResponse> resp = controller.indexDocument(
                validTraceparent(), "doc-1", "nr-1", null);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        IndexResponse body = resp.getBody();
        assertThat(body.getNodeRunId()).isEqualTo("nr-1");
        assertThat(body.getDocumentId()).isEqualTo("doc-1");
        assertThat(body.getIndexName()).isEqualTo("ig_central_documents");
        assertThat(body.getVersion()).isEqualTo(7);
        verify(jobs, times(1)).markRunning("nr-1");
        verify(jobs, times(1)).markCompleted(eq("nr-1"), anyString());
    }

    @Test
    void missing_document_marks_failed_with_DOCUMENT_NOT_FOUND() {
        when(indexingService.indexDocument(any())).thenThrow(new DocumentNotFoundException("doc-missing"));

        assertThatThrownBy(() -> controller.indexDocument(validTraceparent(), "doc-missing", "nr-2", null))
                .isInstanceOf(DocumentNotFoundException.class);

        verify(jobs, times(1)).markFailed(eq("nr-2"), eq("DOCUMENT_NOT_FOUND"), anyString());
    }

    @Test
    void mapping_conflict_marks_failed_with_INDEX_MAPPING_CONFLICT() {
        when(indexingService.indexDocument(any()))
                .thenThrow(new MappingConflictException("doc-x", 400, "{\"error\":\"boom\"}"));

        assertThatThrownBy(() -> controller.indexDocument(validTraceparent(), "doc-x", "nr-3", null))
                .isInstanceOf(MappingConflictException.class);

        verify(jobs, times(1)).markFailed(eq("nr-3"), eq("INDEX_MAPPING_CONFLICT"), anyString());
    }

    @Test
    void backend_unavailable_marks_failed_with_INDEX_BACKEND_UNAVAILABLE() {
        when(indexingService.indexDocument(any()))
                .thenThrow(new IndexBackendUnavailableException("ES down"));

        assertThatThrownBy(() -> controller.indexDocument(validTraceparent(), "doc-y", "nr-4", null))
                .isInstanceOf(IndexBackendUnavailableException.class);

        verify(jobs, times(1)).markFailed(eq("nr-4"), eq("INDEX_BACKEND_UNAVAILABLE"), anyString());
    }

    @Test
    void in_flight_collision_throws_JobInFlightException_and_skips_service() {
        when(jobs.tryAcquire("nr-busy")).thenReturn(JobAcquisition.running(pendingRow("nr-busy")));

        assertThatThrownBy(() -> controller.indexDocument(validTraceparent(), "doc-1", "nr-busy", null))
                .isInstanceOf(JobInFlightException.class);

        verify(indexingService, never()).indexDocument(any());
    }

    @Test
    void completed_idempotency_returns_cached_response() throws Exception {
        IndexResponse cached = new IndexResponse();
        cached.setNodeRunId("nr-cached");
        cached.setDocumentId("doc-1");
        cached.setIndexName("ig_central_documents");
        cached.setVersion(3);
        cached.setDurationMs(15);
        String json = mapper.writeValueAsString(cached);
        Instant now = Instant.now();
        when(jobs.tryAcquire("nr-cached")).thenReturn(JobAcquisition.completed(
                new JobRecord("nr-cached", JobState.COMPLETED, now, now, now, json, null, null,
                        now.plusSeconds(60))));

        ResponseEntity<IndexResponse> resp = controller.indexDocument(
                validTraceparent(), "doc-1", "nr-cached", null);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().getNodeRunId()).isEqualTo("nr-cached");
        verify(indexingService, never()).indexDocument(any());
    }

    @Test
    void delete_returns_DELETED_when_es_removes_the_document() {
        when(indexingService.removeDocument("doc-1"))
                .thenReturn(new IndexingService.DeleteOutcome("doc-1", "DELETED"));

        ResponseEntity<DeleteResponse> resp = controller.deleteDocumentIndex(validTraceparent(), "doc-1");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().getResult()).isEqualTo(DeleteResponse.ResultEnum.DELETED);
    }

    @Test
    void delete_returns_NOT_FOUND_when_document_was_not_indexed() {
        when(indexingService.removeDocument("doc-2"))
                .thenReturn(new IndexingService.DeleteOutcome("doc-2", "NOT_FOUND"));

        ResponseEntity<DeleteResponse> resp = controller.deleteDocumentIndex(validTraceparent(), "doc-2");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().getResult()).isEqualTo(DeleteResponse.ResultEnum.NOT_FOUND);
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
