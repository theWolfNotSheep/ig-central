package co.uk.wolfnotsheep.extraction.tika.web;

/**
 * Extracted text exceeds the configured inline byte ceiling and the
 * MinIO sink (which would emit a {@code textRef}) is not yet wired.
 * Mapped to RFC 7807 {@code EXTRACTION_TOO_LARGE} / HTTP 413.
 *
 * <p>Once the sink lands this exception goes away — large payloads
 * will follow the {@code textRef} path instead.
 */
public class DocumentTooLargeException extends RuntimeException {
    public DocumentTooLargeException(String message) {
        super(message);
    }
}
