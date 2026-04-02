package co.uk.wolfnotsheep.document.services;

import io.minio.*;
import io.minio.errors.ErrorResponseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;

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

        this.minioClient = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
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
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .stream(data, size, -1)
                    .contentType(contentType)
                    .build());
            log.info("Uploaded object: {}/{} ({} bytes)", bucket, key, size);
        } catch (Exception e) {
            throw new RuntimeException("Failed to upload object: " + bucket + "/" + key, e);
        }
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

    public String getDefaultBucket() { return defaultBucket; }
}
