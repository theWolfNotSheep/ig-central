package co.uk.wolfnotsheep.indexing.service;

public class MappingConflictException extends RuntimeException {
    private final String documentId;
    private final int httpStatus;
    private final String responseBody;

    public MappingConflictException(String documentId, int httpStatus, String responseBody) {
        super("ES rejected document " + documentId + " with HTTP " + httpStatus);
        this.documentId = documentId;
        this.httpStatus = httpStatus;
        this.responseBody = responseBody;
    }

    public String documentId() { return documentId; }
    public int httpStatus() { return httpStatus; }
    public String responseBody() { return responseBody; }
}
