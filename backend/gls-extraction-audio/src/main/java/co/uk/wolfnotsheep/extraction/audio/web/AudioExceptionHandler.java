package co.uk.wolfnotsheep.extraction.audio.web;

import co.uk.wolfnotsheep.extraction.audio.parse.AudioCorruptException;
import co.uk.wolfnotsheep.extraction.audio.parse.AudioNotConfiguredException;
import co.uk.wolfnotsheep.extraction.audio.source.DocumentEtagMismatchException;
import co.uk.wolfnotsheep.extraction.audio.source.DocumentNotFoundException;
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
public class AudioExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(AudioExceptionHandler.class);
    private static final URI PROBLEM_BASE = URI.create("https://gls.local/errors/");

    @ExceptionHandler(DocumentNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(DocumentNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "DOCUMENT_NOT_FOUND", "Document not found", e.getMessage());
    }

    @ExceptionHandler(DocumentEtagMismatchException.class)
    public ResponseEntity<ProblemDetail> handleEtag(DocumentEtagMismatchException e) {
        return problem(HttpStatus.CONFLICT, "DOCUMENT_ETAG_MISMATCH",
                "Document changed since the caller's reference", e.getMessage());
    }

    @ExceptionHandler(AudioCorruptException.class)
    public ResponseEntity<ProblemDetail> handleCorrupt(AudioCorruptException e) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "AUDIO_CORRUPT",
                "Audio could not be transcribed", e.getMessage());
    }

    @ExceptionHandler(AudioNotConfiguredException.class)
    public ResponseEntity<ProblemDetail> handleNotConfigured(AudioNotConfiguredException e) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "AUDIO_NOT_CONFIGURED",
                "No transcription provider configured for this build", e.getMessage());
    }

    @ExceptionHandler(DocumentTooLargeException.class)
    public ResponseEntity<ProblemDetail> handleTooLarge(DocumentTooLargeException e) {
        return problem(HttpStatus.PAYLOAD_TOO_LARGE, "AUDIO_TOO_LARGE",
                "Source audio exceeds the configured ceiling", e.getMessage());
    }

    @ExceptionHandler(JobInFlightException.class)
    public ResponseEntity<ProblemDetail> handleInFlight(JobInFlightException e) {
        return problem(HttpStatus.CONFLICT, "IDEMPOTENCY_IN_FLIGHT",
                "An audio transcription with this nodeRunId is still in flight", e.getMessage());
    }

    @ExceptionHandler(JobNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleJobNotFound(JobNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "AUDIO_JOB_NOT_FOUND",
                "No transcription job exists for this nodeRunId", e.getMessage());
    }

    @ExceptionHandler(UncheckedIOException.class)
    public ResponseEntity<ProblemDetail> handleIo(UncheckedIOException e) {
        log.warn("source-side I/O failure: {}", e.getMessage(), e);
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "AUDIO_SOURCE_UNAVAILABLE",
                "Source storage or transcription provider is unreachable", e.getMessage());
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
