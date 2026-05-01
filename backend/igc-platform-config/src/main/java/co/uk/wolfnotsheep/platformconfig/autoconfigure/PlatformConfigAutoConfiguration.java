package co.uk.wolfnotsheep.platformconfig.autoconfigure;

import co.uk.wolfnotsheep.platformconfig.cache.ConfigCacheRegistry;
import co.uk.wolfnotsheep.platformconfig.listen.ConfigChangeDispatcher;
import co.uk.wolfnotsheep.platformconfig.listen.ConfigChangeListener;
import co.uk.wolfnotsheep.platformconfig.publish.ConfigChangePublisher;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import java.util.List;

/**
 * Boots the config-cache primitives.
 *
 * <ul>
 *     <li>{@link ConfigCacheRegistry} always registers — the cache layer
 *         works without a broker (consumers can use it as a plain in-memory
 *         cache when offline / in tests).</li>
 *     <li>{@link ConfigChangePublisher}, {@link ConfigChangeDispatcher},
 *         and the {@code igc.config} {@link TopicExchange} only register
 *         when {@link RabbitTemplate} is on the classpath.</li>
 * </ul>
 *
 * <p>Registered via
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}.
 */
@AutoConfiguration
public class PlatformConfigAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ConfigCacheRegistry configCacheRegistry() {
        return new ConfigCacheRegistry();
    }

    @Bean
    @ConditionalOnMissingBean(name = "platformConfigObjectMapper")
    public ObjectMapper platformConfigObjectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    @Bean
    @ConditionalOnClass(RabbitTemplate.class)
    @ConditionalOnMissingBean(name = "igcConfigExchange")
    public TopicExchange igcConfigExchange() {
        return new TopicExchange("igc.config", true, false);
    }

    @Bean
    @ConditionalOnClass(RabbitTemplate.class)
    @ConditionalOnMissingBean
    public ConfigChangePublisher configChangePublisher(
            RabbitTemplate rabbitTemplate,
            ObjectMapper platformConfigObjectMapper,
            @Value("${spring.application.name:unknown}") String actor,
            ObjectProvider<MeterRegistry> meterRegistryProvider,
            @Value("${igc.platform.config.publisher.retry.buffer-size:1024}") int retryBufferSize) {
        return new ConfigChangePublisher(
                rabbitTemplate, platformConfigObjectMapper, actor,
                meterRegistryProvider, retryBufferSize);
    }

    @Bean
    @ConditionalOnClass(RabbitTemplate.class)
    @ConditionalOnMissingBean
    public ConfigChangeDispatcher configChangeDispatcher(
            ConfigCacheRegistry registry,
            List<ConfigChangeListener> listeners,
            ObjectMapper platformConfigObjectMapper) {
        return new ConfigChangeDispatcher(registry, listeners, platformConfigObjectMapper);
    }
}
