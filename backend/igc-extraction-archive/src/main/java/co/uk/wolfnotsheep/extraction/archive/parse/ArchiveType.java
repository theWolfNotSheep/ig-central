package co.uk.wolfnotsheep.extraction.archive.parse;

/**
 * Logical archive families this service handles. Surfaced on the
 * contract's {@code ExtractResponse.archiveType} field. Distinct from
 * the wire mime type ({@code detectedMimeType}) — the type is what
 * walker handled the bytes; the mime is Tika's authoritative detection.
 */
public enum ArchiveType {
    ZIP,
    MBOX,
    PST
}
