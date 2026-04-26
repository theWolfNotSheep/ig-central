package co.uk.wolfnotsheep.platformconfig.publish;

import co.uk.wolfnotsheep.platformconfig.event.ChangeType;
import co.uk.wolfnotsheep.platformconfig.event.ConfigChangedEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

/**
 * Publishes {@link ConfigChangedEvent}s to the {@code gls.config}
 * topic exchange under routing key {@code config.changed}. Single
 * emitter — services don't call {@link RabbitTemplate#convertAndSend(String, String, Object)}
 * for cache invalidation directly.
 *
 * <p>Per CSV #30, both local writes (admin UI, API mutations) and
 * Hub-driven imports ({@code PackImportService}) MUST publish here so
 * the contract is single-channel regardless of where the change came
 * from.
 *
 * <p>Failures are <em>logged, not raised</em>. A failed publish leaves
 * peer replicas with stale cache entries until the next write — that's
 * a transient correctness gap, not a reason to fail the originating
 * write transaction. The asyncapi declaration documents this trade-off
 * (no DLX; events are inherently retryable via the next mutation).
 */
public class ConfigChangePublisher {

    private static final Logger log = LoggerFactory.getLogger(ConfigChangePublisher.class);
    private static final String EXCHANGE = "gls.config";
    private static final String ROUTING_KEY = "config.changed";

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper mapper;
    private final String actor;

    public ConfigChangePublisher(RabbitTemplate rabbitTemplate, ObjectMapper mapper, String actor) {
        this.rabbitTemplate = rabbitTemplate;
        this.mapper = mapper;
        this.actor = actor;
    }

    /** Single-entity convenience. */
    public void publishSingle(String entityType, String entityId, ChangeType changeType) {
        publish(new ConfigChangedEvent(
                entityType, List.of(entityId), changeType, Instant.now(), actor, null));
    }

    /** Bulk wildcard convenience — invalidates the whole entity-type cache. */
    public void publishBulk(String entityType, ChangeType changeType) {
        publish(new ConfigChangedEvent(
                entityType, List.of(), changeType, Instant.now(), actor, null));
    }

    /** Targeted multi-entity convenience. */
    public void publishMany(String entityType, List<String> entityIds, ChangeType changeType) {
        publish(new ConfigChangedEvent(
                entityType, entityIds, changeType, Instant.now(), actor, null));
    }

    /** Direct publish — caller controls every field including {@code traceparent}. */
    public void publish(ConfigChangedEvent event) {
        byte[] body;
        try {
            body = mapper.writeValueAsBytes(event);
        } catch (JsonProcessingException e) {
            log.warn("config publisher: serialisation failed for entityType={}, dropping event ({})",
                    event.entityType(), e.getOriginalMessage());
            return;
        }
        try {
            rabbitTemplate.send(EXCHANGE, ROUTING_KEY, MessageBuilder
                    .withBody(body)
                    .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                    .setContentEncoding("utf-8")
                    .build());
            log.debug("config publisher: published entityType={} ids={} changeType={}",
                    event.entityType(), event.entityIds(), event.changeType());
        } catch (AmqpException e) {
            log.warn("config publisher: AMQP publish failed for entityType={} — peers may serve stale entries until next write ({})",
                    event.entityType(), e.getMessage());
        }
    }
}
