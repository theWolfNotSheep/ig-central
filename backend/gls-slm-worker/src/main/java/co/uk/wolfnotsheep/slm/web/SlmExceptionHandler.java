package co.uk.wolfnotsheep.slm.web;

import co.uk.wolfnotsheep.slm.backend.BlockUnknownException;
import co.uk.wolfnotsheep.slm.backend.SlmNotConfiguredException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.io.UncheckedIOException;
import java.net.URI;
import java.time.Instant;

@RestControllerAdvice
public class SlmExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(SlmExceptionHandler.class);
    private static final URI PROBLEM_BASE = URI.create("https://gls.local/errors/");

    @ExceptionHandler(SlmNotConfiguredException.class)
    public ResponseEntity<ProblemDetail> handleNotConfigured(SlmNotConfiguredException e) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "SLM_NOT_CONFIGURED",
                "No SLM backend is configured", e.getMessage());
    }

    @ExceptionHandler(BlockUnknownException.class)
    public ResponseEntity<ProblemDetail> handleBlockUnknown(BlockUnknownException e) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "SLM_BLOCK_UNKNOWN",
                "PROMPT block coords did not resolve", e.getMessage());
    }

    @ExceptionHandler(JobInFlightException.class)
    public ResponseEntity<ProblemDetail> handleInFlight(JobInFlightException e) {
        return problem(HttpStatus.CONFLICT, "IDEMPOTENCY_IN_FLIGHT",
                "An SLM classify call with this nodeRunId is still in flight", e.getMessage());
    }

    @ExceptionHandler(JobNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleJobNotFound(JobNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "SLM_JOB_NOT_FOUND",
                "No SLM job exists for this nodeRunId", e.getMessage());
    }

    @ExceptionHandler(UncheckedIOException.class)
    public ResponseEntity<ProblemDetail> handleIo(UncheckedIOException e) {
        log.warn("slm I/O failure: {}", e.getMessage(), e);
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "SLM_DEPENDENCY_UNAVAILABLE",
                "An SLM backend dependency is unreachable", e.getMessage());
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
