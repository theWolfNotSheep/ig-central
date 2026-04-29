package co.uk.wolfnotsheep.bert.web;

import co.uk.wolfnotsheep.bert.inference.BlockUnknownException;
import co.uk.wolfnotsheep.bert.inference.ModelNotLoadedException;
import co.uk.wolfnotsheep.bert.registry.ReloadInProgressException;
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
public class BertExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(BertExceptionHandler.class);
    private static final URI PROBLEM_BASE = URI.create("https://gls.local/errors/");

    @ExceptionHandler(ModelNotLoadedException.class)
    public ResponseEntity<ProblemDetail> handleNotLoaded(ModelNotLoadedException e) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "MODEL_NOT_LOADED",
                "No BERT model is loaded on this replica", e.getMessage());
    }

    @ExceptionHandler(BlockUnknownException.class)
    public ResponseEntity<ProblemDetail> handleBlockUnknown(BlockUnknownException e) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "BLOCK_UNKNOWN",
                "Block coordinates do not resolve to a registered artefact", e.getMessage());
    }

    @ExceptionHandler(ReloadInProgressException.class)
    public ResponseEntity<ProblemDetail> handleReloadInFlight(ReloadInProgressException e) {
        return problem(HttpStatus.CONFLICT, "MODEL_RELOAD_IN_PROGRESS",
                "A model reload is already in progress on this replica", e.getMessage());
    }

    @ExceptionHandler(UncheckedIOException.class)
    public ResponseEntity<ProblemDetail> handleIo(UncheckedIOException e) {
        log.warn("bert: I/O failure: {}", e.getMessage(), e);
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "BERT_DEPENDENCY_UNAVAILABLE",
                "Model artefact storage is unreachable", e.getMessage());
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
