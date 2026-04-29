package co.uk.wolfnotsheep.extraction.archive.sink;

/**
 * Result of {@link ChildSink#upload}: the MinIO coordinates of a child
 * just landed by the archive walk. Surfaces on the contract's
 * {@code ChildDocument.documentRef} response branch — the orchestrator
 * uses {@code bucket} / {@code objectKey} / {@code etag} to commit a
 * fresh {@code DocumentModel} row and publish the child's
 * {@code gls.documents.ingested} event.
 *
 * @param bucket    Bucket the child was written to.
 * @param objectKey Object key within {@code bucket}.
 * @param etag      MinIO ETag of the written object — null if the
 *                  underlying SDK didn't return one.
 * @param size      Byte length actually written.
 */
public record ChildRef(String bucket, String objectKey, String etag, long size) {
}
