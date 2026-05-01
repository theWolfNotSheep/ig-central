package co.uk.wolfnotsheep.extraction.tika.parse;

/**
 * Result of a single {@link TikaExtractionService#extract} call. Captures
 * just what the {@code POST /v1/extract} response needs (per
 * {@code contracts/extraction/openapi.yaml}) plus a {@code byteCount} for
 * the {@code costUnits} computation per CSV #22.
 *
 * @param text             Extracted text. Never null; empty string if
 *                         Tika produced no readable content.
 * @param detectedMimeType Tika's detection. Authoritative; the caller's
 *                         hint is ignored once this is set.
 * @param pageCount        Pages parsed for paginated inputs (PDFs,
 *                         Office docs); {@code null} for stream formats.
 * @param byteCount        Source bytes parsed. Drives the contract's
 *                         {@code costUnits} ({@code byteCount / 1024}).
 * @param truncated        True when the configured character ceiling
 *                         was hit and the text was cut. Caller decides
 *                         how to surface this to the user.
 */
public record ExtractedText(
        String text,
        String detectedMimeType,
        Integer pageCount,
        long byteCount,
        boolean truncated
) {
}
