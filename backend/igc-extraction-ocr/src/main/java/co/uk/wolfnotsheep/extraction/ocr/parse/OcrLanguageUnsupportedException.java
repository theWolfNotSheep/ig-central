package co.uk.wolfnotsheep.extraction.ocr.parse;

/**
 * One or more requested Tesseract language packs aren't installed on
 * this build. Controller maps to RFC 7807
 * {@code OCR_LANGUAGE_UNSUPPORTED} / HTTP 422.
 */
public class OcrLanguageUnsupportedException extends RuntimeException {
    public OcrLanguageUnsupportedException(String message) {
        super(message);
    }
}
