package co.uk.wolfnotsheep.extraction.ocr.web;

import co.uk.wolfnotsheep.extraction.ocr.idempotency.IdempotencyInFlightException;
import co.uk.wolfnotsheep.extraction.ocr.parse.OcrLanguageUnsupportedException;
import co.uk.wolfnotsheep.extraction.ocr.parse.UnparseableImageException;
import co.uk.wolfnotsheep.extraction.ocr.source.DocumentEtagMismatchException;
import co.uk.wolfnotsheep.extraction.ocr.source.DocumentNotFoundException;
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
public class OcrExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(OcrExceptionHandler.class);
    private static final URI PROBLEM_BASE = URI.create("https://igc.local/errors/");

    @ExceptionHandler(DocumentNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(DocumentNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "DOCUMENT_NOT_FOUND", "Document not found", e.getMessage());
    }

    @ExceptionHandler(DocumentEtagMismatchException.class)
    public ResponseEntity<ProblemDetail> handleEtag(DocumentEtagMismatchException e) {
        return problem(HttpStatus.CONFLICT, "DOCUMENT_ETAG_MISMATCH",
                "Document changed since the caller's reference", e.getMessage());
    }

    @ExceptionHandler(UnparseableImageException.class)
    public ResponseEntity<ProblemDetail> handleUnparseable(UnparseableImageException e) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "OCR_CORRUPT",
                "Image / PDF could not be OCR'd", e.getMessage());
    }

    @ExceptionHandler(OcrLanguageUnsupportedException.class)
    public ResponseEntity<ProblemDetail> handleLanguage(OcrLanguageUnsupportedException e) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "OCR_LANGUAGE_UNSUPPORTED",
                "Requested Tesseract language pack is not installed", e.getMessage());
    }

    @ExceptionHandler(DocumentTooLargeException.class)
    public ResponseEntity<ProblemDetail> handleTooLarge(DocumentTooLargeException e) {
        return problem(HttpStatus.PAYLOAD_TOO_LARGE, "OCR_TOO_LARGE",
                "Source exceeds the configured ceiling", e.getMessage());
    }

    @ExceptionHandler(IdempotencyInFlightException.class)
    public ResponseEntity<ProblemDetail> handleInFlight(IdempotencyInFlightException e) {
        return problem(HttpStatus.CONFLICT, "IDEMPOTENCY_IN_FLIGHT",
                "An OCR with this nodeRunId is still in flight", e.getMessage());
    }

    @ExceptionHandler(UncheckedIOException.class)
    public ResponseEntity<ProblemDetail> handleIo(UncheckedIOException e) {
        log.warn("source-side I/O failure: {}", e.getMessage(), e);
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "OCR_SOURCE_UNAVAILABLE",
                "Source storage is unreachable", e.getMessage());
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
