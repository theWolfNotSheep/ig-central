package co.uk.wolfnotsheep.platformaudit.autoconfigure;

import co.uk.wolfnotsheep.platformaudit.emit.AuditEmitter;
import co.uk.wolfnotsheep.platformaudit.emit.OutboxAuditEmitter;
import co.uk.wolfnotsheep.platformaudit.outbox.AuditOutboxRepository;
import co.uk.wolfnotsheep.platformaudit.relay.OutboxRelay;
import co.uk.wolfnotsheep.platformaudit.relay.OutboxRelayMetrics;
import co.uk.wolfnotsheep.platformaudit.relay.OutboxRelayProperties;
import co.uk.wolfnotsheep.platformaudit.relay.OutboxStartupReplay;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

/**
 * Boots the audit outbox primitives without consumers needing to know
 * about this library's package layout. Activates whenever Spring Data
 * Mongo is on the classpath; consumers can override the emitter with
 * their own {@link AuditEmitter} bean if they need to.
 *
 * <p>The {@link OutboxRelay} only activates when {@link RabbitTemplate}
 * is on the classpath (i.e. the consumer pulls in
 * {@code spring-boot-starter-amqp}) and
 * {@code igc.platform.audit.relay.enabled} is not set to {@code false}.
 * Consumers must declare {@code @EnableScheduling} for the relay to tick.
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
     * Declares the audit topic exchange so consumers don't have to. If the
     * exchange already exists with the same configuration this is a no-op
     * at the broker; if it exists with a different configuration RabbitMQ
     * will refuse the declaration — a deployment problem to flag loudly,
     * not silently work around.
     */
    @Bean
    @ConditionalOnClass(RabbitTemplate.class)
    @ConditionalOnMissingBean(name = "igcAuditExchange")
    public TopicExchange igcAuditExchange(OutboxRelayProperties properties) {
        return new TopicExchange(properties.exchange(), true, false);
    }

    @Bean
    @ConditionalOnClass(RabbitTemplate.class)
    @ConditionalOnMissingBean(OutboxRelayMetrics.class)
    public OutboxRelayMetrics outboxRelayMetrics(
            MeterRegistry registry, AuditOutboxRepository repository) {
        return new OutboxRelayMetrics(registry, repository);
    }

    @Bean
    @ConditionalOnClass(RabbitTemplate.class)
    @ConditionalOnProperty(prefix = "igc.platform.audit.relay", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean(OutboxRelay.class)
    public OutboxRelay outboxRelay(
            AuditOutboxRepository repository,
            RabbitTemplate rabbitTemplate,
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
    @ConditionalOnClass(RabbitTemplate.class)
    @ConditionalOnProperty(prefix = "igc.platform.audit.relay", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean(OutboxStartupReplay.class)
    public OutboxStartupReplay outboxStartupReplay(
            MongoTemplate mongoTemplate,
            ObjectProvider<MeterRegistry> meterRegistryProvider) {
        return new OutboxStartupReplay(mongoTemplate, meterRegistryProvider);
    }
}
