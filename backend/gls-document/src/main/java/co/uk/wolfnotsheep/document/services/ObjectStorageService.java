package co.uk.wolfnotsheep.document.services;

import io.minio.*;
import io.minio.errors.ErrorResponseException;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

/**
 * Abstraction over MinIO/S3-compatible object storage.
 * Handles raw file upload, download, copy (for tier migration), and deletion.
 */
@Service
public class ObjectStorageService {

    private static final Logger log = LoggerFactory.getLogger(ObjectStorageService.class);

    private final MinioClient minioClient;
    private final String defaultBucket;

    public ObjectStorageService(
            @Value("${minio.endpoint:http://localhost:9000}") String endpoint,
            @Value("${minio.access-key:minioadmin}") String accessKey,
            @Value("${minio.secret-key:minioadmin}") String secretKey,
            @Value("${minio.bucket:gls-documents}") String defaultBucket) {

        OkHttpClient httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();

        this.minioClient = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .httpClient(httpClient)
                .build();
        this.defaultBucket = defaultBucket;
    }

    public void ensureBucketExists(String bucket) {
        try {
            if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("Created bucket: {}", bucket);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to ensure bucket exists: " + bucket, e);
        }
    }

    public void upload(String key, InputStream data, long size, String contentType) {
        upload(defaultBucket, key, data, size, contentType);
    }

    public void upload(String bucket, String key, InputStream data, long size, String contentType) {
        try {
            ensureBucketExists(bucket);

            // Buffer the stream so we can retry if verification fails
            byte[] bytes = data.readAllBytes();

            doUpload(bucket, key, bytes, contentType);

            // Post-upload verification
            if (!exists(bucket, key)) {
                log.warn("Upload verification failed for {}/{}, retrying once", bucket, key);
                doUpload(bucket, key, bytes, contentType);
                if (!exists(bucket, key)) {
                    throw new RuntimeException(
                            "Upload verification failed after retry: " + bucket + "/" + key);
                }
            }

            log.info("Uploaded object: {}/{} ({} bytes)", bucket, key, bytes.length);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to upload object: " + bucket + "/" + key, e);
        }
    }

    private void doUpload(String bucket, String key, byte[] bytes, String contentType) throws Exception {
        minioClient.putObject(PutObjectArgs.builder()
                .bucket(bucket)
                .object(key)
                .stream(new ByteArrayInputStream(bytes), bytes.length, -1)
                .contentType(contentType)
                .build());
    }

    public InputStream download(String key) {
        return download(defaultBucket, key);
    }

    public InputStream download(String bucket, String key) {
        try {
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .build());
        } catch (Exception e) {
            throw new RuntimeException("Failed to download object: " + bucket + "/" + key, e);
        }
    }

    public void copy(String sourceBucket, String sourceKey, String destBucket, String destKey) {
        try {
            ensureBucketExists(destBucket);
            minioClient.copyObject(CopyObjectArgs.builder()
                    .bucket(destBucket)
                    .object(destKey)
                    .source(CopySource.builder()
                            .bucket(sourceBucket)
                            .object(sourceKey)
                            .build())
                    .build());
            log.info("Copied object: {}/{} → {}/{}", sourceBucket, sourceKey, destBucket, destKey);
        } catch (Exception e) {
            throw new RuntimeException("Failed to copy object", e);
        }
    }

    public void delete(String key) {
        delete(defaultBucket, key);
    }

    public void delete(String bucket, String key) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .build());
            log.info("Deleted object: {}/{}", bucket, key);
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete object: " + bucket + "/" + key, e);
        }
    }

    public boolean exists(String key) {
        return exists(defaultBucket, key);
    }

    public boolean exists(String bucket, String key) {
        try {
            minioClient.statObject(StatObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .build());
            return true;
        } catch (ErrorResponseException e) {
            return false;
        } catch (Exception e) {
            throw new RuntimeException("Failed to check object existence", e);
        }
    }

    public boolean healthCheck() {
        try {
            minioClient.bucketExists(BucketExistsArgs.builder().bucket(defaultBucket).build());
            return true;
        } catch (Exception e) {
            log.warn("MinIO health check failed: {}", e.getMessage());
            return false;
        }
    }

    public String getDefaultBucket() { return defaultBucket; }
}
