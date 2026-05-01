package co.uk.wolfnotsheep.indexing.web;

import co.uk.wolfnotsheep.indexing.service.DocumentNotFoundException;
import co.uk.wolfnotsheep.indexing.service.IndexBackendUnavailableException;
import co.uk.wolfnotsheep.indexing.service.MappingConflictException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;

@RestControllerAdvice
public class IndexingExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(IndexingExceptionHandler.class);
    private static final URI PROBLEM_BASE = URI.create("https://igc.local/errors/");

    @ExceptionHandler(DocumentNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleDocumentNotFound(DocumentNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "DOCUMENT_NOT_FOUND",
                "Referenced document does not exist", e.getMessage());
    }

    @ExceptionHandler(MappingConflictException.class)
    public ResponseEntity<ProblemDetail> handleMappingConflict(MappingConflictException e) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "INDEX_MAPPING_CONFLICT",
                "Elasticsearch rejected the document — parked in index_quarantine", e.getMessage());
    }

    @ExceptionHandler(IndexBackendUnavailableException.class)
    public ResponseEntity<ProblemDetail> handleBackendUnavailable(IndexBackendUnavailableException e) {
        log.warn("indexing backend unavailable: {}", e.getMessage());
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "INDEX_BACKEND_UNAVAILABLE",
                "Elasticsearch is unreachable or returned 5xx", e.getMessage());
    }

    @ExceptionHandler(JobInFlightException.class)
    public ResponseEntity<ProblemDetail> handleInFlight(JobInFlightException e) {
        return problem(HttpStatus.CONFLICT, "IDEMPOTENCY_IN_FLIGHT",
                "An indexing operation with this nodeRunId is still in flight", e.getMessage());
    }

    @ExceptionHandler(JobNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleJobNotFound(JobNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "INDEXING_JOB_NOT_FOUND",
                "No indexing job exists for this nodeRunId", e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleIllegalArgument(IllegalArgumentException e) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "INDEX_INVALID_INPUT",
                "Request payload failed validation", e.getMessage());
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(RuntimeException e) {
        log.error("indexing unexpected failure: {}", e.getMessage(), e);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "INDEX_UNEXPECTED",
                "Unexpected indexing failure", e.getMessage());
    }

    private static ResponseEntity<ProblemDetail> problem(
            HttpStatus status, String code, String title, String detail) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, detail);
        body.setTitle(title);
        body.setType(PROBLEM_BASE.resolve(code));
        body.setProperty("code", code);
        body.setProperty("retryable", retryable(status));
        body.setProperty("timestamp", Instant.now().toString());
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(body);
    }

    private static boolean retryable(HttpStatus status) {
        return status == HttpStatus.SERVICE_UNAVAILABLE
                || status == HttpStatus.TOO_MANY_REQUESTS
                || status.is5xxServerError();
    }
}
