package co.uk.wolfnotsheep.extraction.ocr.parse;

import java.util.List;

/**
 * Result of one {@link OcrExtractionService#run} call.
 *
 * @param text             OCR-extracted text. Never null; empty string
 *                         if the engine produced nothing readable.
 * @param detectedMimeType Tika's detected mime for the source bytes.
 * @param languages        Languages effectively used (echo of the
 *                         request's `languages`, defaulted to
 *                         {@code ["eng"]} if absent).
 * @param pageCount        Pages OCR'd (multi-page PDF / TIFF); null
 *                         for single-page images.
 * @param byteCount        Source bytes consumed; drives `costUnits`
 *                         per CSV #22.
 */
public record OcrResult(
        String text,
        String detectedMimeType,
        List<String> languages,
        Integer pageCount,
        long byteCount
) {
}
