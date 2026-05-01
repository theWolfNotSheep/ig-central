package co.uk.wolfnotsheep.extraction.archive.source;

/**
 * The {@link DocumentRef} carried an ETag that did not match the
 * storage-side ETag at fetch time — the document changed between the
 * caller's reference and our read. Controller maps to RFC 7807 with
 * code {@code DOCUMENT_ETAG_MISMATCH} and HTTP 409.
 */
public class DocumentEtagMismatchException extends RuntimeException {
    public DocumentEtagMismatchException(DocumentRef ref, String actualEtag) {
        super("etag mismatch on " + ref.bucket() + "/" + ref.objectKey()
                + " — expected " + ref.etag() + ", got " + actualEtag);
    }
}
