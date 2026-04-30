package co.uk.wolfnotsheep.router.parse;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;

/**
 * HTTP wiring for the cascade tiers that talk to other v2 workers
 * over HTTP. Each tier is independently activated by its own feature
 * flag — without the flag, the dispatcher bean isn't constructed and
 * {@link CascadeBackendConfig} composes the cascade without that
 * tier.
 */
@Configuration
public class RouterHttpConfig {

    @Bean
    @ConditionalOnProperty(prefix = "gls.router.cascade.bert", name = "enabled", havingValue = "true")
    public BertHttpDispatcher bertHttpDispatcher(
            @Value("${gls.router.cascade.bert.url:http://gls-bert-inference:8080}") String url,
            @Value("${gls.router.cascade.bert.timeout-ms:30000}") int timeoutMs,
            ObjectMapper mapper) {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        return new BertHttpDispatcher(client, URI.create(url), Duration.ofMillis(timeoutMs), mapper);
    }

    @Bean
    @ConditionalOnProperty(prefix = "gls.router.cascade.slm", name = "enabled", havingValue = "true")
    public SlmHttpDispatcher slmHttpDispatcher(
            @Value("${gls.router.cascade.slm.url:http://gls-slm-worker:8080}") String url,
            @Value("${gls.router.cascade.slm.timeout-ms:60000}") int timeoutMs,
            ObjectMapper mapper) {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        return new SlmHttpDispatcher(client, URI.create(url), Duration.ofMillis(timeoutMs), mapper);
    }

    @Bean
    @ConditionalOnProperty(prefix = "gls.router.cascade.llm-http", name = "enabled", havingValue = "true")
    public LlmBudgetGate llmBudgetGate(ObjectProvider<MeterRegistry> meterRegistryProvider) {
        return new LlmBudgetGate(meterRegistryProvider.getIfAvailable());
    }

    @Bean
    @ConditionalOnProperty(prefix = "gls.router.cascade.llm-http", name = "enabled", havingValue = "true")
    public LlmHttpDispatcher llmHttpDispatcher(
            @Value("${gls.router.cascade.llm-http.url:http://gls-llm-worker:8080}") String url,
            @Value("${gls.router.cascade.llm-http.timeout-ms:90000}") int timeoutMs,
            @Value("${gls.router.cascade.llm-http.budget-fallback-retry-after:PT1H}") Duration budgetFallback,
            LlmBudgetGate budgetGate,
            ObjectMapper mapper) {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        return new LlmHttpDispatcher(client, URI.create(url), Duration.ofMillis(timeoutMs),
                mapper, budgetGate, budgetFallback);
    }
}
