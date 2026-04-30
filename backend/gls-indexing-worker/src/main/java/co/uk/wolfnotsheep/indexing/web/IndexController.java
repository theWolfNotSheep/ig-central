package co.uk.wolfnotsheep.indexing.web;

import co.uk.wolfnotsheep.indexing.api.IndexApi;
import co.uk.wolfnotsheep.indexing.jobs.JobAcquisition;
import co.uk.wolfnotsheep.indexing.jobs.JobStore;
import co.uk.wolfnotsheep.indexing.model.DeleteResponse;
import co.uk.wolfnotsheep.indexing.model.IndexResponse;
import co.uk.wolfnotsheep.indexing.service.DocumentNotFoundException;
import co.uk.wolfnotsheep.indexing.service.IndexBackendUnavailableException;
import co.uk.wolfnotsheep.indexing.service.IndexingService;
import co.uk.wolfnotsheep.indexing.service.MappingConflictException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * Sync admin escape hatches: re-index a single document or remove
 * one from the index. Idempotency on `nodeRunId` per CSV #16 +
 * CSV #47 — same {@link JobStore} row pattern as the sibling workers.
 */
@RestController
public class IndexController implements IndexApi {

    private static final Logger log = LoggerFactory.getLogger(IndexController.class);

    private final IndexingService indexingService;
    private final JobStore jobs;
    private final ObjectMapper mapper;

    public IndexController(IndexingService indexingService, JobStore jobs, ObjectMapper mapper) {
        this.indexingService = indexingService;
        this.jobs = jobs;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<IndexResponse> indexDocument(
            String traceparent, String documentId, String nodeRunId, String idempotencyKey) {

        JobAcquisition acq = jobs.tryAcquire(nodeRunId);
        return switch (acq.status()) {
            case ACQUIRED -> performSync(documentId, nodeRunId);
            case RUNNING -> { throw new JobInFlightException(nodeRunId); }
            case COMPLETED -> ResponseEntity.ok(deserialise(acq.existing().resultJson()));
            case FAILED -> { throw new JobInFlightException(nodeRunId); }
        };
    }

    private ResponseEntity<IndexResponse> performSync(String documentId, String nodeRunId) {
        try {
            jobs.markRunning(nodeRunId);
            Instant started = Instant.now();
            IndexingService.IndexOutcome outcome = indexingService.indexDocument(documentId);
            long durationMs = Math.max(0L, java.time.Duration.between(started, Instant.now()).toMillis());

            IndexResponse body = new IndexResponse();
            body.setNodeRunId(nodeRunId);
            body.setDocumentId(outcome.documentId());
            body.setIndexName(outcome.indexName());
            body.setVersion((int) Math.min(Integer.MAX_VALUE, Math.max(0L, outcome.version())));
            body.setDurationMs((int) Math.min(Integer.MAX_VALUE, durationMs));
            cacheCompleted(nodeRunId, body);
            return ResponseEntity.ok(body);
        } catch (RuntimeException failure) {
            String code = errorCodeFor(failure);
            jobs.markFailed(nodeRunId, code, failure.getMessage() == null
                    ? failure.getClass().getSimpleName() : failure.getMessage());
            throw failure;
        }
    }

    @Override
    public ResponseEntity<DeleteResponse> deleteDocumentIndex(String traceparent, String documentId) {
        IndexingService.DeleteOutcome outcome = indexingService.removeDocument(documentId);
        DeleteResponse body = new DeleteResponse();
        body.setDocumentId(outcome.documentId());
        body.setResult(DeleteResponse.ResultEnum.fromValue(outcome.result()));
        return ResponseEntity.ok(body);
    }

    private IndexResponse deserialise(String json) {
        try {
            return mapper.readValue(json, IndexResponse.class);
        } catch (JsonProcessingException e) {
            log.warn("idempotency cache deserialise failed: {}", e.getMessage());
            throw new IllegalStateException("idempotency cache row was unparseable", e);
        }
    }

    private void cacheCompleted(String nodeRunId, IndexResponse response) {
        try {
            jobs.markCompleted(nodeRunId, mapper.writeValueAsString(response));
        } catch (JsonProcessingException e) {
            log.warn("indexing cache write failed for nodeRunId={}: {}", nodeRunId, e.getMessage());
        }
    }

    private static String errorCodeFor(Throwable cause) {
        if (cause instanceof DocumentNotFoundException) return "DOCUMENT_NOT_FOUND";
        if (cause instanceof MappingConflictException) return "INDEX_MAPPING_CONFLICT";
        if (cause instanceof IndexBackendUnavailableException) return "INDEX_BACKEND_UNAVAILABLE";
        if (cause instanceof JobInFlightException) return "IDEMPOTENCY_IN_FLIGHT";
        return "INDEX_UNEXPECTED";
    }
}
