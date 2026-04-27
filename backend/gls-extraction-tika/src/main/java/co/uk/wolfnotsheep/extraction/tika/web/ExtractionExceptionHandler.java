package co.uk.wolfnotsheep.extraction.tika.web;

import co.uk.wolfnotsheep.extraction.tika.parse.UnparseableDocumentException;
import co.uk.wolfnotsheep.extraction.tika.source.DocumentEtagMismatchException;
import co.uk.wolfnotsheep.extraction.tika.source.DocumentNotFoundException;
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
 * (CSV #17). Lives in the web layer so the parse + source layers stay
 * MVC-agnostic and unit-testable.
 *
 * <p>The {@code code} extension field carries the ig-central error
 * taxonomy (e.g. {@code DOCUMENT_NOT_FOUND}, {@code EXTRACTION_CORRUPT}).
 * Concrete codes must align with what the spec advertises so generated
 * client SDKs can switch on them.
 */
@RestControllerAdvice
public class ExtractionExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ExtractionExceptionHandler.class);
    private static final URI PROBLEM_BASE = URI.create("https://gls.local/errors/");

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

    @ExceptionHandler(UnparseableDocumentException.class)
    public ResponseEntity<ProblemDetail> handleUnparseable(UnparseableDocumentException e) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "EXTRACTION_CORRUPT",
                "Document could not be parsed",
                e.getMessage());
    }

    @ExceptionHandler(DocumentTooLargeException.class)
    public ResponseEntity<ProblemDetail> handleTooLarge(DocumentTooLargeException e) {
        return problem(HttpStatus.PAYLOAD_TOO_LARGE, "EXTRACTION_TOO_LARGE",
                "Extracted text exceeds the configured ceiling",
                e.getMessage());
    }

    @ExceptionHandler(UncheckedIOException.class)
    public ResponseEntity<ProblemDetail> handleIo(UncheckedIOException e) {
        log.warn("source-side I/O failure: {}", e.getMessage(), e);
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "EXTRACTION_SOURCE_UNAVAILABLE",
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
