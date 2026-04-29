package co.uk.wolfnotsheep.extraction.archive.parse;

import java.io.InputStream;

/**
 * Callback the {@link ArchiveWalker} invokes for each direct child it
 * encounters. The walker provides a streaming view of the entry's
 * bytes; the implementation (typically the controller) is responsible
 * for materialising them — usually by uploading to MinIO via
 * {@code co.uk.wolfnotsheep.extraction.archive.sink.ChildSink}.
 *
 * <p>Distinct from
 * {@code co.uk.wolfnotsheep.extraction.archive.sink.ChildSink}: that
 * is the storage-side interface; this one is the parse-time emit hook
 * the walker invokes once per entry. Implementations receive entries
 * in the archive's natural traversal order.
 */
@FunctionalInterface
public interface ChildEmitter {

    /**
     * Handle one child entry. The {@code content} stream is positioned
     * at the entry's bytes; the walker treats {@code content} as
     * exclusively owned by the implementation for the duration of this
     * call. Implementations must fully read the stream before returning
     * — the walker may advance / reuse the underlying archive stream
     * once this method returns.
     *
     * @param fileName        the entry's name as it appeared in the archive.
     * @param archivePath     path within the archive (or null for flat structures).
     * @param contentTypeHint walker's guess at the mime type ({@code null} if none).
     * @param size            uncompressed size in bytes if the walker knows; -1 otherwise.
     * @param content         bytes of the entry.
     * @throws co.uk.wolfnotsheep.extraction.archive.parse.ArchiveCapsExceededException
     *         if the implementation determines a per-archive cap has
     *         been hit. The walker propagates without continuing.
     */
    void onChild(
            String fileName,
            String archivePath,
            String contentTypeHint,
            long size,
            InputStream content);
}
