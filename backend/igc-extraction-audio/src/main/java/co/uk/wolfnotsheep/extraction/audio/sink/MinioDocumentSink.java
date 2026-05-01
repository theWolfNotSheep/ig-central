package co.uk.wolfnotsheep.extraction.audio.sink;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;

public class MinioDocumentSink implements DocumentSink {

    private static final Logger log = LoggerFactory.getLogger(MinioDocumentSink.class);
    private static final String CONTENT_TYPE = "text/plain;charset=utf-8";

    private final MinioClient minio;
    private final String bucket;

    public MinioDocumentSink(MinioClient minio, String bucket) {
        this.minio = minio;
        this.bucket = bucket;
    }

    @Override
    public ExtractedTextRef upload(String nodeRunId, String text) {
        ensureBucketExists();
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        String objectKey = "audio/" + nodeRunId + ".txt";
        try (var in = new ByteArrayInputStream(bytes)) {
            minio.putObject(PutObjectArgs.builder()
                    .bucket(bucket).object(objectKey)
                    .stream(in, bytes.length, -1)
                    .contentType(CONTENT_TYPE).build());
            log.debug("sink: uploaded {} bytes to {}/{}", bytes.length, bucket, objectKey);
            return new ExtractedTextRef(
                    URI.create("minio://" + bucket + "/" + objectKey), bytes.length, CONTENT_TYPE);
        } catch (IOException e) {
            throw new UncheckedIOException("MinIO put failed: " + bucket + "/" + objectKey, e);
        } catch (Exception e) {
            throw new UncheckedIOException("MinIO put failed: " + bucket + "/" + objectKey,
                    e instanceof IOException io ? io : new IOException(e));
        }
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
