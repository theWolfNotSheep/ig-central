package co.uk.wolfnotsheep.extraction.archive.parse;

/**
 * The detected mime type doesn't correspond to any registered walker.
 * Controller maps to RFC 7807 with code
 * {@code ARCHIVE_UNSUPPORTED_TYPE} / HTTP 422.
 */
public class UnsupportedArchiveTypeException extends RuntimeException {
    public UnsupportedArchiveTypeException(String detectedMime) {
        super("no walker supports archive mime type: " + detectedMime);
    }
}
