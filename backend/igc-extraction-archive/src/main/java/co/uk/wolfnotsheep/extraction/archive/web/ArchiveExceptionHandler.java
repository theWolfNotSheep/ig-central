package co.uk.wolfnotsheep.extraction.archive.web;

import co.uk.wolfnotsheep.extraction.archive.idempotency.IdempotencyInFlightException;
import co.uk.wolfnotsheep.extraction.archive.parse.ArchiveCapsExceededException;
import co.uk.wolfnotsheep.extraction.archive.parse.CorruptArchiveException;
import co.uk.wolfnotsheep.extraction.archive.parse.UnsupportedArchiveTypeException;
import co.uk.wolfnotsheep.extraction.archive.source.DocumentEtagMismatchException;
import co.uk.wolfnotsheep.extraction.archive.source.DocumentNotFoundException;
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

/**
 * Maps the controller's domain exceptions to RFC 7807 problem details
 * (CSV #17). The {@code code} extension carries the ig-central error
 * taxonomy and matches the {@code errorCode} on the corresponding
 * {@code EXTRACTION_FAILED} audit event.
 */
@RestControllerAdvice
public class ArchiveExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ArchiveExceptionHandler.class);
    private static final URI PROBLEM_BASE = URI.create("https://igc.local/errors/");

    @ExceptionHandler(DocumentNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(DocumentNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "DOCUMENT_NOT_FOUND", "Document not found",
                e.getMessage());
    }

    @ExceptionHandler(DocumentEtagMismatchException.class)
    public ResponseEntity<ProblemDetail> handleEtag(DocumentEtagMismatchException e) {
        return problem(HttpStatus.CONFLICT, "DOCUMENT_ETAG_MISMATCH",
                "Document changed since the caller's reference",
                e.getMessage());
    }

    @ExceptionHandler(CorruptArchiveException.class)
    public ResponseEntity<ProblemDetail> handleCorrupt(CorruptArchiveException e) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "ARCHIVE_CORRUPT",
                "Archive could not be parsed",
                e.getMessage());
    }

    @ExceptionHandler(UnsupportedArchiveTypeException.class)
    public ResponseEntity<ProblemDetail> handleUnsupported(UnsupportedArchiveTypeException e) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "ARCHIVE_UNSUPPORTED_TYPE",
                "Archive mime type not supported by this build",
                e.getMessage());
    }

    @ExceptionHandler(ArchiveCapsExceededException.class)
    public ResponseEntity<ProblemDetail> handleCaps(ArchiveCapsExceededException e) {
        // The cap enum maps to the contract's documented `code`
        // extension on 413 — orchestrator distinguishes which cap was
        // hit by reading `code`.
        return problem(HttpStatus.PAYLOAD_TOO_LARGE, e.cap().name(),
                "Archive exceeds a configured cap",
                e.getMessage());
    }

    @ExceptionHandler(IdempotencyInFlightException.class)
    public ResponseEntity<ProblemDetail> handleInFlight(IdempotencyInFlightException e) {
        return problem(HttpStatus.CONFLICT, "IDEMPOTENCY_IN_FLIGHT",
                "An unpack with this nodeRunId is still in flight",
                e.getMessage());
    }

    @ExceptionHandler(UncheckedIOException.class)
    public ResponseEntity<ProblemDetail> handleIo(UncheckedIOException e) {
        log.warn("source-side I/O failure: {}", e.getMessage(), e);
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "ARCHIVE_SOURCE_UNAVAILABLE",
                "Source storage is unreachable",
                e.getMessage());
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
