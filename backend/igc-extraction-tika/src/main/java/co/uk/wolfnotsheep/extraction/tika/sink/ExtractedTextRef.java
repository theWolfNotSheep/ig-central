package co.uk.wolfnotsheep.extraction.tika.sink;

import java.net.URI;

/**
 * Out-of-band reference to extracted text stored in MinIO. Returned by
 * {@link DocumentSink#upload} and surfaced on the contract's
 * {@code textRef} response branch when an extraction exceeds the inline
 * byte ceiling per CSV #19.
 *
 * @param uri           MinIO object URI in the form {@code minio://bucket/key}.
 *                      Callers resolve to a real signed URL via the
 *                      configured MinIO endpoint when they fetch.
 * @param contentLength Byte length of the uploaded text.
 * @param contentType   MIME type — always {@code text/plain;charset=utf-8}
 *                      for this sink, but kept on the response shape per
 *                      the contract.
 */
public record ExtractedTextRef(URI uri, long contentLength, String contentType) {
}
