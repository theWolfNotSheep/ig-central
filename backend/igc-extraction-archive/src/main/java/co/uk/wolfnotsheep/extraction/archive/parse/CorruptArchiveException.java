package co.uk.wolfnotsheep.extraction.archive.parse;

/**
 * The archive bytes could not be parsed by the walker that claimed to
 * support them — typically truncated, password-protected, or in a
 * variant the walker doesn't handle (e.g. an mbox-cl with non-standard
 * separators). Controller maps to RFC 7807 with code
 * {@code ARCHIVE_CORRUPT} / HTTP 422.
 */
public class CorruptArchiveException extends RuntimeException {
    public CorruptArchiveException(String message) {
        super(message);
    }

    public CorruptArchiveException(String message, Throwable cause) {
        super(message, cause);
    }
}
