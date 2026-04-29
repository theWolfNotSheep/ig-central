package co.uk.wolfnotsheep.extraction.archive.parse;

/**
 * Thrown when a configured per-archive cap is hit during the walk.
 * Mapped to RFC 7807 with HTTP 413 and one of the {@code code}
 * extensions below — the orchestrator distinguishes which cap was hit
 * by reading {@code code}.
 */
public class ArchiveCapsExceededException extends RuntimeException {

    public enum Cap {
        /** Source archive bytes exceed the configured ceiling. */
        ARCHIVE_TOO_LARGE,
        /** Number of direct children exceeds the configured maximum. */
        ARCHIVE_TOO_MANY_CHILDREN,
        /** A single child's uncompressed size exceeds the per-child cap (zip-bomb defence). */
        ARCHIVE_CHILD_TOO_LARGE
    }

    private final Cap cap;

    public ArchiveCapsExceededException(Cap cap, String message) {
        super(message);
        this.cap = cap;
    }

    public Cap cap() {
        return cap;
    }
}
