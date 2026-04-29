package co.uk.wolfnotsheep.extraction.ocr.parse;

/**
 * Tesseract refused to parse the source bytes — corrupt image,
 * unsupported format variant, or bytes that aren't actually an image.
 * Controller maps to RFC 7807 {@code OCR_CORRUPT} / HTTP 422.
 */
public class UnparseableImageException extends RuntimeException {
    public UnparseableImageException(String message, Throwable cause) {
        super(message, cause);
    }
}
