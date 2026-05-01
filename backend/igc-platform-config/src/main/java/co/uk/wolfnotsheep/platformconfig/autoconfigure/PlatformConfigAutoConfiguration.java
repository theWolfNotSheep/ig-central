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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Boots the config-cache primitives.
 *
 * <ul>
 *     <li>{@link ConfigCacheRegistry} always registers — the cache layer
 *         works without a broker (consumers can use it as a plain in-memory
 *         cache when offline / in tests).</li>
 *     <li>The publisher, dispatcher, and {@code igc.config} topic exchange
 *         only register when {@code RabbitTemplate} is on the classpath.
 *         Those beans live in the nested {@link RabbitConfig} so that
 *         consumers without {@code spring-boot-starter-amqp} never load
 *         method signatures referencing rabbit types — Spring's bean-method
 *         introspection eagerly resolves return and parameter types via
 *         {@code Class.getDeclaredMethods()}, so a method-level
 *         {@code @ConditionalOnClass} is not enough.</li>
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

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.amqp.rabbit.core.RabbitTemplate")
    static class RabbitConfig {

        @Bean
        @ConditionalOnMissingBean(name = "igcConfigExchange")
        public org.springframework.amqp.core.TopicExchange igcConfigExchange() {
            return new org.springframework.amqp.core.TopicExchange("igc.config", true, false);
        }

        @Bean
        @ConditionalOnMissingBean
        public ConfigChangePublisher configChangePublisher(
                org.springframework.amqp.rabbit.core.RabbitTemplate rabbitTemplate,
                ObjectMapper platformConfigObjectMapper,
                @Value("${spring.application.name:unknown}") String actor,
                ObjectProvider<MeterRegistry> meterRegistryProvider,
                @Value("${igc.platform.config.publisher.retry.buffer-size:1024}") int retryBufferSize) {
            return new ConfigChangePublisher(
                    rabbitTemplate, platformConfigObjectMapper, actor,
                    meterRegistryProvider, retryBufferSize);
        }

        @Bean
        @ConditionalOnMissingBean
        public ConfigChangeDispatcher configChangeDispatcher(
                ConfigCacheRegistry registry,
                List<ConfigChangeListener> listeners,
                ObjectMapper platformConfigObjectMapper) {
            return new ConfigChangeDispatcher(registry, listeners, platformConfigObjectMapper);
        }
    }
}
