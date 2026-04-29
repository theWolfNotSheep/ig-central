package co.uk.wolfnotsheep.extraction.ocr.source;

public class DocumentEtagMismatchException extends RuntimeException {
    public DocumentEtagMismatchException(DocumentRef ref, String actualEtag) {
        super("etag mismatch on " + ref.bucket() + "/" + ref.objectKey()
                + " — expected " + ref.etag() + ", got " + actualEtag);
    }
}
