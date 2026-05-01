package co.uk.wolfnotsheep.extraction.tika.source;

/**
 * In-process pointer to a MinIO-backed document. Mirrors the
 * {@code documentRef} object on the OpenAPI spec; lives in this package
 * so the source layer doesn't depend on the generated model classes
 * (which carry generator-specific annotations the source layer doesn't
 * need).
 *
 * @param bucket    MinIO bucket holding the source bytes.
 * @param objectKey MinIO object key within {@code bucket}.
 * @param etag      Optional ETag — when present, the source layer
 *                  verifies it before streaming and rejects on mismatch.
 *                  Null disables the check.
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
