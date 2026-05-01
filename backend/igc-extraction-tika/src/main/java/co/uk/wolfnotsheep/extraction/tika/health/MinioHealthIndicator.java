package co.uk.wolfnotsheep.extraction.tika.health;

import io.minio.MinioClient;
import io.minio.messages.Bucket;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Probes MinIO connectivity by listing buckets. The list call hits the
 * broker over the SDK's HTTP client; a connection refused / timeout
 * surfaces as DOWN under {@code /actuator/health/minio} so the
 * readiness gate doesn't flip UP before the service can fetch
 * documents.
 *
 * <p>The result count is reported as a {@code bucketCount} detail so
 * an operator can sanity-check the credentials are pointing at the
 * intended cluster.
 */
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
