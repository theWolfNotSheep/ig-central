package co.uk.wolfnotsheep.extraction.tika.source;

/**
 * The {@link DocumentRef} pointed at a bucket / objectKey that doesn't
 * exist. Controller maps to RFC 7807 with code
 * {@code DOCUMENT_NOT_FOUND} and HTTP 404.
 */
public class DocumentNotFoundException extends RuntimeException {
    public DocumentNotFoundException(DocumentRef ref) {
        super("document not found: " + ref.bucket() + "/" + ref.objectKey());
    }
}
