package co.uk.wolfnotsheep.extraction.archive.parse;

import java.io.InputStream;

/**
 * Strategy interface for one archive format. Implementations advertise
 * which {@link ArchiveType} they handle; the dispatcher routes a
 * {@code POST /v1/extract} request to the matching walker by Tika's
 * detected mime type.
 *
 * <p>Walkers are stateless and thread-safe — one instance per family
 * is wired as a Spring bean and reused across requests.
 */
public interface ArchiveWalker {

    /** Logical archive family this walker handles. */
    ArchiveType supports();

    /**
     * Walk {@code input} and invoke {@code emitter} once per direct
     * child found. Caller is responsible for closing {@code input}.
     *
     * @param input    raw archive bytes.
     * @param fileName the source archive's name (provides extension
     *                 hints to dispatch).
     * @param emitter  callback for each direct child.
     * @throws CorruptArchiveException if the bytes don't represent a
     *         valid instance of the family this walker handles.
     * @throws ArchiveCapsExceededException re-thrown from the emitter
     *         when a per-archive cap is hit; walker stops without
     *         draining the rest of the entries.
     * @throws java.io.UncheckedIOException on stream-level I/O failure.
     */
    void walk(InputStream input, String fileName, ChildEmitter emitter);
}
