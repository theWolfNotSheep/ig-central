package co.uk.wolfnotsheep.extraction.ocr.source;

public class DocumentNotFoundException extends RuntimeException {
    public DocumentNotFoundException(DocumentRef ref) {
        super("document not found: " + ref.bucket() + "/" + ref.objectKey());
    }
}
