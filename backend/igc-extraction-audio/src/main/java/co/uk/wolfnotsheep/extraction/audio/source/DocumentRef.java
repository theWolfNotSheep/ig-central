package co.uk.wolfnotsheep.extraction.audio.source;

public record DocumentRef(String bucket, String objectKey, String etag) {

    public DocumentRef {
        if (bucket == null || bucket.isBlank()) throw new IllegalArgumentException("bucket is required");
        if (objectKey == null || objectKey.isBlank()) throw new IllegalArgumentException("objectKey is required");
    }

    public static DocumentRef of(String bucket, String objectKey) {
        return new DocumentRef(bucket, objectKey, null);
    }

    public static DocumentRef withEtag(String bucket, String objectKey, String etag) {
        return new DocumentRef(bucket, objectKey, etag);
    }
}
