package co.uk.wolfnotsheep.extraction.tika.source;

import java.io.InputStream;

/**
 * Resolves a {@link DocumentRef} to its bytes. Decoupled from the
 * MinIO SDK so the controller can be unit-tested against a fake source
 * without standing up a MinIO container.
 */
public interface DocumentSource {

    /**
     * Open a fresh stream over the document's bytes. Caller is
     * responsible for closing.
     *
     * @throws DocumentNotFoundException if {@code ref} doesn't exist.
     * @throws DocumentEtagMismatchException if {@code ref.etag()} is set
     *         and the storage-side ETag doesn't match.
     * @throws java.io.UncheckedIOException for any other I/O failure.
     */
    InputStream open(DocumentRef ref);

    /** Best-effort byte length of the object, or -1 if unknown. */
    long sizeOf(DocumentRef ref);
}
