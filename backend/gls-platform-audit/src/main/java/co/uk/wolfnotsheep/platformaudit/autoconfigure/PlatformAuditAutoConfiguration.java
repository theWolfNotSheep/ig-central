package co.uk.wolfnotsheep.platformaudit.autoconfigure;

import co.uk.wolfnotsheep.platformaudit.emit.AuditEmitter;
import co.uk.wolfnotsheep.platformaudit.emit.OutboxAuditEmitter;
import co.uk.wolfnotsheep.platformaudit.outbox.AuditOutboxRepository;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

/**
 * Boots the audit outbox primitives without consumers needing to know
 * about this library's package layout. Activates whenever Spring Data
 * Mongo is on the classpath; consumers can override the emitter with
 * their own {@link AuditEmitter} bean if they need to.
 *
 * <p>Registered via {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}.
 */
@AutoConfiguration
@ConditionalOnClass(MongoTemplate.class)
@EnableMongoRepositories(basePackageClasses = AuditOutboxRepository.class)
public class PlatformAuditAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(AuditEmitter.class)
    public AuditEmitter outboxAuditEmitter(AuditOutboxRepository outboxRepository) {
        return new OutboxAuditEmitter(outboxRepository);
    }
}
