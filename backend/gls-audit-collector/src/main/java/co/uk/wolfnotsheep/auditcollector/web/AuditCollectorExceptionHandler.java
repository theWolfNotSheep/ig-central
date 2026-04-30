package co.uk.wolfnotsheep.auditcollector.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;

@RestControllerAdvice
public class AuditCollectorExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(AuditCollectorExceptionHandler.class);
    private static final URI PROBLEM_BASE = URI.create("https://gls.local/errors/");

    @ExceptionHandler(AuditEventNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleEventNotFound(AuditEventNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "AUDIT_EVENT_NOT_FOUND",
                "No audit event with this id", e.getMessage());
    }

    @ExceptionHandler(AuditResourceNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleResourceNotFound(AuditResourceNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "AUDIT_RESOURCE_NOT_FOUND",
                "No Tier 1 events recorded for this resource", e.getMessage());
    }

    @ExceptionHandler(AuditQueryInvalidException.class)
    public ResponseEntity<ProblemDetail> handleInvalidQuery(AuditQueryInvalidException e) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "AUDIT_QUERY_INVALID",
                "Query parameters failed validation", e.getMessage());
    }

    @ExceptionHandler(DataAccessResourceFailureException.class)
    public ResponseEntity<ProblemDetail> handleBackendUnavailable(DataAccessResourceFailureException e) {
        log.warn("audit backend unavailable: {}", e.getMessage());
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "AUDIT_BACKEND_UNAVAILABLE",
                "Audit backend is unreachable", e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleIllegalArgument(IllegalArgumentException e) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "AUDIT_QUERY_INVALID",
                "Request payload failed validation", e.getMessage());
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(RuntimeException e) {
        log.error("audit collector unexpected failure: {}", e.getMessage(), e);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "AUDIT_UNEXPECTED",
                "Unexpected audit-collector failure", e.getMessage());
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
