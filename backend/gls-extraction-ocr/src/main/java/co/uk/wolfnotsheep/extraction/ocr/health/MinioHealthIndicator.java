package co.uk.wolfnotsheep.extraction.ocr.health;

import io.minio.MinioClient;
import io.minio.messages.Bucket;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MinioHealthIndicator implements HealthIndicator {

    private final MinioClient minio;

    public MinioHealthIndicator(MinioClient minio) {
        this.minio = minio;
    }

    @Override
    public Health health() {
        try {
            List<Bucket> buckets = minio.listBuckets();
            return Health.up().withDetail("bucketCount", buckets.size()).build();
        } catch (Exception e) {
            return Health.down().withException(e).build();
        }
    }
}
