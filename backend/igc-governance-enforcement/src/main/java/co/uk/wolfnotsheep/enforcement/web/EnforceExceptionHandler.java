package co.uk.wolfnotsheep.enforcement.web;

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

/**
 * Maps service-specific exceptions to RFC 7807 ProblemDetails per
 * {@code contracts/_shared/error-envelope.yaml} (CSV #17). Mirrors
 * the slm-worker / classifier-router exception-handler shape so the
 * envelope is consistent across the v2 service set.
 */
@RestControllerAdvice
public class EnforceExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(EnforceExceptionHandler.class);
    private static final URI PROBLEM_BASE = URI.create("https://igc.local/errors/");

    @ExceptionHandler(DocumentNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleDocumentNotFound(DocumentNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "DOCUMENT_NOT_FOUND",
                "Referenced document does not exist", e.getMessage());
    }

    @ExceptionHandler(EnforcementInvalidInputException.class)
    public ResponseEntity<ProblemDetail> handleInvalidInput(EnforcementInvalidInputException e) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "ENFORCEMENT_INVALID_INPUT",
                "Classification event failed validation", e.getMessage());
    }

    @ExceptionHandler(JobInFlightException.class)
    public ResponseEntity<ProblemDetail> handleInFlight(JobInFlightException e) {
        return problem(HttpStatus.CONFLICT, "IDEMPOTENCY_IN_FLIGHT",
                "An enforcement call with this nodeRunId is still in flight", e.getMessage());
    }

    @ExceptionHandler(JobNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleJobNotFound(JobNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "ENFORCEMENT_JOB_NOT_FOUND",
                "No enforcement job exists for this nodeRunId", e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleIllegalArgument(IllegalArgumentException e) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "ENFORCEMENT_INVALID_INPUT",
                "Request payload failed validation", e.getMessage());
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(RuntimeException e) {
        log.error("enforcement unexpected failure: {}", e.getMessage(), e);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "ENFORCEMENT_UNEXPECTED",
                "Unexpected enforcement failure", e.getMessage());
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
