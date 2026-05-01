package co.uk.wolfnotsheep.extraction.ocr.sink;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MinioSinkConfig {

    @Bean
    @ConditionalOnMissingBean
    public DocumentSink documentSink(
            MinioClient minio,
            @Value("${igc.extraction.ocr.sink.bucket:igc-ocr-text}") String bucket) {
        return new MinioDocumentSink(minio, bucket);
    }
}
