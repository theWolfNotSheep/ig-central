package co.uk.wolfnotsheep.router.parse;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;

/**
 * HTTP wiring for the BERT cascade tier. Activated when
 * {@code gls.router.cascade.bert.enabled=true}; without that flag the
 * orchestrator is not constructed and the router behaves exactly as
 * before (mock or LLM-direct).
 *
 * <p>The orchestrator itself is composed in
 * {@link CascadeBackendConfig#cascadeService} so it can wrap whichever
 * inner cascade is configured (LLM if enabled, mock otherwise).
 */
@Configuration
@ConditionalOnProperty(prefix = "gls.router.cascade.bert", name = "enabled", havingValue = "true")
public class RouterHttpConfig {

    @Bean
    public BertHttpDispatcher bertHttpDispatcher(
            @Value("${gls.router.cascade.bert.url:http://gls-bert-inference:8080}") String url,
            @Value("${gls.router.cascade.bert.timeout-ms:30000}") int timeoutMs,
            ObjectMapper mapper) {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        return new BertHttpDispatcher(client, URI.create(url), Duration.ofMillis(timeoutMs), mapper);
    }
}
