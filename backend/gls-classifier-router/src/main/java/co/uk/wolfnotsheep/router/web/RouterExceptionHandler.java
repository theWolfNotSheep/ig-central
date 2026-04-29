package co.uk.wolfnotsheep.router.web;

import co.uk.wolfnotsheep.router.idempotency.IdempotencyInFlightException;
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
public class RouterExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(RouterExceptionHandler.class);
    private static final URI PROBLEM_BASE = URI.create("https://gls.local/errors/");

    @ExceptionHandler(BlockNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleBlockMissing(BlockNotFoundException e) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "ROUTER_BLOCK_NOT_FOUND",
                "Block id / version did not resolve", e.getMessage());
    }

    @ExceptionHandler(IdempotencyInFlightException.class)
    public ResponseEntity<ProblemDetail> handleInFlight(IdempotencyInFlightException e) {
        return problem(HttpStatus.CONFLICT, "IDEMPOTENCY_IN_FLIGHT",
                "A classify call with this nodeRunId is still in flight", e.getMessage());
    }

    @ExceptionHandler(UncheckedIOException.class)
    public ResponseEntity<ProblemDetail> handleIo(UncheckedIOException e) {
        log.warn("router I/O failure: {}", e.getMessage(), e);
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "ROUTER_DEPENDENCY_UNAVAILABLE",
                "A downstream cascade tier is unreachable", e.getMessage());
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
