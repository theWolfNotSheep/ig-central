package co.uk.wolfnotsheep.extraction.tika.sink;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the {@link MinioDocumentSink} bean. Reuses the shared
 * {@link MinioClient} bean defined in
 * {@code co.uk.wolfnotsheep.extraction.tika.source.MinioSourceConfig}.
 */
@Configuration
public class MinioSinkConfig {

    @Bean
    @ConditionalOnMissingBean
    public DocumentSink documentSink(
            MinioClient minio,
            @Value("${gls.extraction.tika.sink.bucket:gls-extracted-text}") String bucket) {
        return new MinioDocumentSink(minio, bucket);
    }
}
