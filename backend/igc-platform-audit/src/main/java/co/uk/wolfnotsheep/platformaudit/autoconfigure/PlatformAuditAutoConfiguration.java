package co.uk.wolfnotsheep.platformaudit.autoconfigure;

import co.uk.wolfnotsheep.platformaudit.emit.AuditEmitter;
import co.uk.wolfnotsheep.platformaudit.emit.OutboxAuditEmitter;
import co.uk.wolfnotsheep.platformaudit.outbox.AuditOutboxRepository;
import co.uk.wolfnotsheep.platformaudit.relay.OutboxRelay;
import co.uk.wolfnotsheep.platformaudit.relay.OutboxRelayMetrics;
import co.uk.wolfnotsheep.platformaudit.relay.OutboxRelayProperties;
import co.uk.wolfnotsheep.platformaudit.relay.OutboxStartupReplay;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

/**
 * Boots the audit outbox primitives without consumers needing to know
 * about this library's package layout. Activates whenever Spring Data
 * Mongo is on the classpath; consumers can override the emitter with
 * their own {@link AuditEmitter} bean if they need to.
 *
 * <p>The relay-related beans (topic exchange, metrics, relay loop, startup
 * replay) only activate when {@code RabbitTemplate} is on the classpath.
 * Spring's bean-method introspection eagerly resolves method-signature
 * types via {@code Class.getDeclaredMethods()}, so the rabbit-typed beans
 * live in a nested {@link RabbitConfig} whose class-level
 * {@code @ConditionalOnClass} skips loading the inner class entirely on
 * consumers without {@code spring-boot-starter-amqp}.
 *
 * <p>Registered via {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}.
 */
@AutoConfiguration
@ConditionalOnClass(MongoTemplate.class)
@EnableMongoRepositories(basePackageClasses = AuditOutboxRepository.class)
@EnableConfigurationProperties(OutboxRelayProperties.class)
public class PlatformAuditAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(AuditEmitter.class)
    public AuditEmitter outboxAuditEmitter(AuditOutboxRepository outboxRepository) {
        return new OutboxAuditEmitter(outboxRepository);
    }

    /**
     * Spring Boot 4 ships Jackson 3 ({@code tools.jackson.core}) as its
     * primary ObjectMapper, but most of this codebase still uses Jackson 2
     * ({@code com.fasterxml.jackson}) — controllers, clients, and platform
     * libraries all expect the Jackson 2 type. Provide one centrally so
     * every consumer of platform-audit gets a default Jackson 2 ObjectMapper
     * for autowiring. Marked {@code @Primary} so it wins over any other
     * Jackson 2 ObjectMapper that consumer modules also register (e.g. the
     * named {@code platformConfigObjectMapper}).
     */
    @Bean
    @Primary
    @ConditionalOnMissingBean(name = "objectMapper")
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.amqp.rabbit.core.RabbitTemplate")
    static class RabbitConfig {

        /**
         * Declares the audit topic exchange so consumers don't have to. If the
         * exchange already exists with the same configuration this is a no-op
         * at the broker; if it exists with a different configuration RabbitMQ
         * will refuse the declaration — a deployment problem to flag loudly,
         * not silently work around.
         */
        @Bean
        @ConditionalOnMissingBean(name = "igcAuditExchange")
        public org.springframework.amqp.core.TopicExchange igcAuditExchange(OutboxRelayProperties properties) {
            return new org.springframework.amqp.core.TopicExchange(properties.exchange(), true, false);
        }

        @Bean
        @ConditionalOnMissingBean(OutboxRelayMetrics.class)
        public OutboxRelayMetrics outboxRelayMetrics(
                MeterRegistry registry, AuditOutboxRepository repository) {
            return new OutboxRelayMetrics(registry, repository);
        }

        @Bean
        @ConditionalOnProperty(prefix = "igc.platform.audit.relay", name = "enabled", havingValue = "true", matchIfMissing = true)
        @ConditionalOnMissingBean(OutboxRelay.class)
        public OutboxRelay outboxRelay(
                AuditOutboxRepository repository,
                org.springframework.amqp.rabbit.core.RabbitTemplate rabbitTemplate,
                OutboxRelayProperties properties,
                OutboxRelayMetrics metrics) {
            return new OutboxRelay(repository, rabbitTemplate, properties, metrics);
        }

        /**
         * Phase 2.1 PR3 — drain backed-off PENDING outbox rows on application
         * restart. Gated by the same property as the relay itself: if the relay
         * isn't running, there's nothing to replay into.
         */
        @Bean
        @ConditionalOnProperty(prefix = "igc.platform.audit.relay", name = "enabled", havingValue = "true", matchIfMissing = true)
        @ConditionalOnMissingBean(OutboxStartupReplay.class)
        public OutboxStartupReplay outboxStartupReplay(
                MongoTemplate mongoTemplate,
                ObjectProvider<MeterRegistry> meterRegistryProvider) {
            return new OutboxStartupReplay(mongoTemplate, meterRegistryProvider);
        }
    }
}
