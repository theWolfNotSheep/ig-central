package co.uk.wolfnotsheep.extraction.tika.health;

import io.minio.MinioClient;
import io.minio.messages.Bucket;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MinioHealthIndicatorTest {

    @Test
    void reachable_minio_reports_UP_with_bucketCount() throws Exception {
        MinioClient minio = mock(MinioClient.class);
        when(minio.listBuckets()).thenReturn(List.of(mock(Bucket.class), mock(Bucket.class)));

        Health h = new MinioHealthIndicator(minio).health();

        assertThat(h.getStatus()).isEqualTo(Status.UP);
        assertThat(h.getDetails()).containsEntry("bucketCount", 2);
    }

    @Test
    void unreachable_minio_reports_DOWN_with_exception_detail() throws Exception {
        MinioClient minio = mock(MinioClient.class);
        when(minio.listBuckets()).thenThrow(new IOException("connection refused"));

        Health h = new MinioHealthIndicator(minio).health();

        assertThat(h.getStatus()).isEqualTo(Status.DOWN);
        assertThat(h.getDetails()).containsKey("error");
    }
}
