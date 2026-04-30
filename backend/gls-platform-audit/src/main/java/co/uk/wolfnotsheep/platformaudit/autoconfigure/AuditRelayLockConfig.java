package co.uk.wolfnotsheep.platformaudit.autoconfigure;

import com.mongodb.client.MongoClient;
import io.micrometer.core.instrument.MeterRegistry;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.mongo.MongoLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * Wires ShedLock leader election for the {@link co.uk.wolfnotsheep.platformaudit.relay.OutboxRelay}.
 * Per CSV #4, only one replica may publish a given Tier 1 envelope —
 * ShedLock provides the gate via a Mongo-backed lock collection
 * ({@code shedLock} by default).
 *
 * <p>Lives in a separate auto-config class because {@link EnableSchedulerLock}
 * is a class-level annotation that doesn't compose cleanly with the
 * {@link PlatformAuditAutoConfiguration} surface (which doesn't depend
 * on ShedLock to function in a single-replica deployment).
 *
 * <p>The whole thing is {@link ConditionalOnClass}({@code MongoLockProvider})
 * so consumers that don't pull in {@code shedlock-provider-mongo}
 * keep the relay running without leader election (single-replica is
 * still safe).
 */
@AutoConfiguration
@ConditionalOnClass({MongoLockProvider.class, MongoTemplate.class})
@ConditionalOnProperty(prefix = "gls.platform.audit.relay", name = "leader-election-enabled",
        havingValue = "true", matchIfMissing = true)
@EnableSchedulerLock(
        defaultLockAtMostFor = "${gls.platform.audit.relay.lock-at-most-for:PT5M}")
public class AuditRelayLockConfig {

    @Bean
    @ConditionalOnMissingBean(LockProvider.class)
    public LockProvider auditRelayLockProvider(
            MongoClient mongoClient,
            @Value("${spring.data.mongodb.database:governance_led_storage_main}") String database,
            ObjectProvider<MeterRegistry> meterRegistryProvider) {
        LockProvider mongo = new MongoLockProvider(mongoClient.getDatabase(database));
        MeterRegistry registry = meterRegistryProvider.getIfAvailable();
        if (registry == null) {
            return mongo;
        }
        return new MetricsLockProvider(mongo, registry);
    }
}
