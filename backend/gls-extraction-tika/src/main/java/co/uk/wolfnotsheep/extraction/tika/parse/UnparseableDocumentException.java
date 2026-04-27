package co.uk.wolfnotsheep.extraction.tika.parse;

/**
 * Tika could not parse the input. Typically means the bytes are corrupt,
 * the format is unsupported, or the parser refused for some other reason
 * (e.g. encrypted archive without a password). The controller surfaces
 * this as RFC 7807 with code {@code EXTRACTION_CORRUPT} and HTTP 422.
 */
public class UnparseableDocumentException extends RuntimeException {
    public UnparseableDocumentException(String message, Throwable cause) {
        super(message, cause);
    }
}
