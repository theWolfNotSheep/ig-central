package co.uk.wolfnotsheep.extraction.archive.sink;

import java.io.InputStream;

/**
 * Writes a single archive child to its destination object store and
 * returns the resulting {@link ChildRef}. Decoupled from the MinIO SDK
 * so the controller is unit-testable against fakes.
 *
 * <p>Object-key strategy is {@code <nodeRunId>/<index>-<sanitised-fileName>}
 * — the {@code nodeRunId} prefix segregates per-invocation outputs
 * cleanly and the {@code index} keeps order deterministic for
 * children that share a name (e.g. an mbox where multiple emails have
 * identical subjects).
 */
public interface ChildSink {

    /**
     * Upload {@code content} as a binary object under {@code nodeRunId}.
     *
     * @param nodeRunId   the archive's node-run id; used as a prefix in
     *                    the destination key.
     * @param index       0-based ordinal of this child in the archive's
     *                    natural traversal order.
     * @param fileName    original entry name as it appeared in the
     *                    archive; preserved on the response shape and
     *                    sanitised into the MinIO key.
     * @param size        expected byte length of {@code content}. -1 if
     *                    unknown to the caller; the sink falls back to
     *                    a streaming put without a content-length hint.
     * @param contentType IANA mime hint for the child; surfaced as the
     *                    object's {@code Content-Type} on the put.
     *                    Null falls back to {@code application/octet-stream}.
     * @param content     the child's bytes. Caller closes the stream.
     * @throws java.io.UncheckedIOException on storage failure.
     */
    ChildRef upload(
            String nodeRunId,
            int index,
            String fileName,
            long size,
            String contentType,
            InputStream content);
}
