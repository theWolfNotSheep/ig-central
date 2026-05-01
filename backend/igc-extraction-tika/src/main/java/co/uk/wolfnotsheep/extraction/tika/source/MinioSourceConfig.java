package co.uk.wolfnotsheep.extraction.tika.source;

import io.minio.MinioClient;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Wires the MinIO SDK client + the {@link MinioDocumentSource} bean.
 * Both beans are {@link ConditionalOnMissingBean} so a test class can
 * override either with a stub.
 *
 * <p>Endpoint / credentials come from the standard {@code minio.*}
 * properties also used by {@code igc-app-assembly}'s
 * {@code ObjectStorageService}.
 */
@Configuration
public class MinioSourceConfig {

    @Bean
    @ConditionalOnMissingBean
    public MinioClient minioClient(
            @Value("${minio.endpoint:http://localhost:9000}") String endpoint,
            @Value("${minio.access-key:minioadmin}") String accessKey,
            @Value("${minio.secret-key:minioadmin}") String secretKey) {
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .httpClient(httpClient)
                .build();
    }

    @Bean
    @ConditionalOnMissingBean
    public DocumentSource documentSource(MinioClient minio) {
        return new MinioDocumentSource(minio);
    }
}
