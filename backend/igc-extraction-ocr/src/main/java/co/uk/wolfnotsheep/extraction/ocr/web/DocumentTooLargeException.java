package co.uk.wolfnotsheep.extraction.ocr.web;

/**
 * Source image / PDF exceeds the configured byte ceiling. Mapped to
 * RFC 7807 {@code OCR_TOO_LARGE} / HTTP 413.
 */
public class DocumentTooLargeException extends RuntimeException {
    public DocumentTooLargeException(String message) {
        super(message);
    }
}
