package co.uk.wolfnotsheep.extraction.archive.sink;

import io.micrometer.observation.annotation.Observed;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.ObjectWriteResponse;
import io.minio.PutObjectArgs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/**
 * MinIO-backed {@link ChildSink}. Writes one archive child per
 * {@link #upload} call.
 *
 * <p>Object key: {@code <nodeRunId>/<index>-<sanitised-fileName>}.
 * The {@code nodeRunId} prefix segregates per-invocation outputs and
 * makes a retried extraction overwrite the same set of objects rather
 * than creating orphans. {@code index} disambiguates same-named entries
 * in the archive's natural traversal order.
 *
 * <p>Bucket creation is best-effort on first write — keeps deployments
 * from needing a separate provisioning step. If the bucket already
 * exists this is a no-op.
 */
public class MinioChildSink implements ChildSink {

    private static final Logger log = LoggerFactory.getLogger(MinioChildSink.class);
    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";
    private static final long PART_SIZE = 5L * 1024 * 1024;

    private final MinioClient minio;
    private final String bucket;

    public MinioChildSink(MinioClient minio, String bucket) {
        this.minio = minio;
        this.bucket = bucket;
    }

    @Override
    @Observed(name = "minio.put",
            contextualName = "minio-put",
            lowCardinalityKeyValues = {"component", "minio"})
    public ChildRef upload(
            String nodeRunId,
            int index,
            String fileName,
            long size,
            String contentType,
            InputStream content) {
        ensureBucketExists();
        String key = childKey(nodeRunId, index, fileName);
        String type = (contentType == null || contentType.isBlank())
                ? DEFAULT_CONTENT_TYPE : contentType;
        long contentLength = size >= 0 ? size : -1L;
        long partSize = contentLength >= 0 ? -1L : PART_SIZE;
        try {
            ObjectWriteResponse resp = minio.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .stream(content, contentLength, partSize)
                    .contentType(type)
                    .build());
            log.debug("sink: wrote child {}/{} (size={}, type={})", bucket, key, size, type);
            return new ChildRef(bucket, key, resp.etag(),
                    contentLength >= 0 ? contentLength : -1L);
        } catch (IOException e) {
            throw new UncheckedIOException("MinIO put failed: " + bucket + "/" + key, e);
        } catch (Exception e) {
            throw new UncheckedIOException("MinIO put failed: " + bucket + "/" + key,
                    e instanceof IOException io ? io : new IOException(e));
        }
    }

    static String childKey(String nodeRunId, int index, String fileName) {
        String safe = sanitise(fileName);
        return nodeRunId + "/" + index + "-" + safe;
    }

    private static String sanitise(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "child";
        }
        // Replace path separators + control chars; keep dots so the
        // file extension survives for downstream mime detection.
        StringBuilder sb = new StringBuilder(fileName.length());
        for (int i = 0; i < fileName.length(); i++) {
            char c = fileName.charAt(i);
            if (c == '/' || c == '\\' || c < 0x20) {
                sb.append('_');
            } else {
                sb.append(c);
            }
        }
        // MinIO objectKey ceiling is 1024 chars; the prefix
        // "<nodeRunId>/<index>-" eats up to ~80, so cap at 900 to be safe.
        if (sb.length() > 900) {
            return sb.substring(0, 900);
        }
        return sb.toString();
    }

    private void ensureBucketExists() {
        try {
            boolean exists = minio.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minio.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("sink: created bucket {}", bucket);
            }
        } catch (Exception e) {
            log.debug("sink: bucketExists/makeBucket failed for {}: {}", bucket, e.getMessage());
        }
    }
}
