package co.uk.wolfnotsheep.extraction.audio.source;

public class DocumentNotFoundException extends RuntimeException {
    public DocumentNotFoundException(DocumentRef ref) {
        super("document not found: " + ref.bucket() + "/" + ref.objectKey());
    }
}
