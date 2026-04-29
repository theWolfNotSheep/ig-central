package co.uk.wolfnotsheep.llmworker.web;

import co.uk.wolfnotsheep.llmworker.backend.BlockUnknownException;
import co.uk.wolfnotsheep.llmworker.backend.BudgetExceededException;
import co.uk.wolfnotsheep.llmworker.backend.LlmNotConfiguredException;
import co.uk.wolfnotsheep.llmworker.backend.RateLimitExceededException;
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
public class LlmExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(LlmExceptionHandler.class);
    private static final URI PROBLEM_BASE = URI.create("https://gls.local/errors/");

    @ExceptionHandler(LlmNotConfiguredException.class)
    public ResponseEntity<ProblemDetail> handleNotConfigured(LlmNotConfiguredException e) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "LLM_NOT_CONFIGURED",
                "No LLM backend is configured", e.getMessage());
    }

    @ExceptionHandler(BlockUnknownException.class)
    public ResponseEntity<ProblemDetail> handleBlockUnknown(BlockUnknownException e) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "LLM_BLOCK_UNKNOWN",
                "PROMPT block coords did not resolve", e.getMessage());
    }

    @ExceptionHandler(JobInFlightException.class)
    public ResponseEntity<ProblemDetail> handleInFlight(JobInFlightException e) {
        return problem(HttpStatus.CONFLICT, "IDEMPOTENCY_IN_FLIGHT",
                "An LLM classify call with this nodeRunId is still in flight", e.getMessage());
    }

    @ExceptionHandler(JobNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleJobNotFound(JobNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "LLM_JOB_NOT_FOUND",
                "No LLM job exists for this nodeRunId", e.getMessage());
    }

    @ExceptionHandler(UncheckedIOException.class)
    public ResponseEntity<ProblemDetail> handleIo(UncheckedIOException e) {
        log.warn("llm I/O failure: {}", e.getMessage(), e);
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "LLM_DEPENDENCY_UNAVAILABLE",
                "An LLM backend dependency is unreachable", e.getMessage());
    }

    @ExceptionHandler(BudgetExceededException.class)
    public ResponseEntity<ProblemDetail> handleBudgetExceeded(BudgetExceededException e) {
        ResponseEntity<ProblemDetail> base = problem(HttpStatus.TOO_MANY_REQUESTS,
                "LLM_BUDGET_EXCEEDED",
                "Daily LLM token budget exceeded", e.getMessage());
        return ResponseEntity.status(base.getStatusCode())
                .headers(h -> {
                    base.getHeaders().forEach(h::addAll);
                    h.add("Retry-After", String.valueOf(e.retryAfterSeconds()));
                })
                .body(base.getBody());
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ProblemDetail> handleRateLimit(RateLimitExceededException e) {
        ResponseEntity<ProblemDetail> base = problem(HttpStatus.TOO_MANY_REQUESTS,
                "LLM_RATE_LIMITED",
                "Per-replica LLM rate limit exceeded", e.getMessage());
        return ResponseEntity.status(base.getStatusCode())
                .headers(h -> {
                    base.getHeaders().forEach(h::addAll);
                    h.add("Retry-After", "1");
                })
                .body(base.getBody());
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
