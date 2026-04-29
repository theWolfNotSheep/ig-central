package co.uk.wolfnotsheep.extraction.archive.sink;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the {@link MinioChildSink} bean. Reuses the shared
 * {@link MinioClient} bean defined in
 * {@code co.uk.wolfnotsheep.extraction.archive.source.MinioSourceConfig}.
 */
@Configuration
public class MinioChildSinkConfig {

    @Bean
    @ConditionalOnMissingBean
    public ChildSink childSink(
            MinioClient minio,
            @Value("${gls.extraction.archive.sink.bucket:gls-archive-children}") String bucket) {
        return new MinioChildSink(minio, bucket);
    }
}
