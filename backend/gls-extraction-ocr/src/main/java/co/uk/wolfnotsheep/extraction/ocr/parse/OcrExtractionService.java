package co.uk.wolfnotsheep.extraction.ocr.parse;

import java.io.InputStream;
import java.util.List;

/**
 * Strategy interface for the OCR engine. The default Spring-wired
 * implementation is {@link Tess4JOcrExtractionService} (Tesseract via
 * JNA). Tests substitute a fake to avoid requiring the native
 * tesseract binary on the build host.
 */
public interface OcrExtractionService {

    /**
     * Run OCR over {@code input} and return the extracted text.
     *
     * @param input     source bytes — image (PNG/JPEG/TIFF/BMP) or
     *                  scanned PDF.
     * @param fileName  filename hint (extension informs Tika's mime
     *                  detect). May be null.
     * @param languages Tesseract language codes — e.g. {@code ["eng"]},
     *                  {@code ["eng","fra"]}. Empty / null defaults to
     *                  {@code ["eng"]}.
     * @return populated {@link OcrResult}.
     * @throws UnparseableImageException if the bytes don't represent a
     *         recognisable image / PDF.
     * @throws OcrLanguageUnsupportedException if any requested language
     *         pack isn't installed on this build.
     * @throws java.io.UncheckedIOException for stream-level I/O failures.
     */
    OcrResult run(InputStream input, String fileName, List<String> languages);
}
