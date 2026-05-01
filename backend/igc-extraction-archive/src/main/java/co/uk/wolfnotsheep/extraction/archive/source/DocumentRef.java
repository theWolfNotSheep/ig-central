package co.uk.wolfnotsheep.extraction.archive.source;

/**
 * In-process pointer to a MinIO-backed object. Mirrors the
 * {@code documentRef} object on the OpenAPI spec; lives in this package
 * so the source layer doesn't depend on the generated model classes
 * (which carry generator-specific annotations the source layer doesn't
 * need).
 */
public record DocumentRef(String bucket, String objectKey, String etag) {

    public DocumentRef {
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalArgumentException("bucket is required");
        }
        if (objectKey == null || objectKey.isBlank()) {
            throw new IllegalArgumentException("objectKey is required");
        }
    }

    public static DocumentRef of(String bucket, String objectKey) {
        return new DocumentRef(bucket, objectKey, null);
    }

    public static DocumentRef withEtag(String bucket, String objectKey, String etag) {
        return new DocumentRef(bucket, objectKey, etag);
    }
}
