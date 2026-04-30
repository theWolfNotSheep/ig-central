package co.uk.wolfnotsheep.indexing.service;

public class DocumentNotFoundException extends RuntimeException {
    public DocumentNotFoundException(String documentId) {
        super("documentId " + documentId + " does not resolve to a document");
    }
}
