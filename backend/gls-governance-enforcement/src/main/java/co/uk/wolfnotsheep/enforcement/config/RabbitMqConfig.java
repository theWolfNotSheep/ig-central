package co.uk.wolfnotsheep.enforcement.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/**
 * RabbitMQ topology for the legacy in-process governance-enforcement path.
 *
 * <p>Phase 2.3 — supports {@code gls.rabbit.quorum-queues.enabled=true}
 * for Raft-replicated queues. See {@code gls-app-assembly}'s
 * {@code RabbitMqConfig} for the migration runbook.
 */
@Configuration
@ConditionalOnProperty(name = "pipeline.execution-engine.enabled", havingValue = "false", matchIfMissing = true)
public class RabbitMqConfig {

    public static final String EXCHANGE = "gls.documents";
    public static final String QUEUE_CLASSIFIED = "gls.documents.classified";
    public static final String ROUTING_CLASSIFIED = "document.classified";

    private final boolean quorumQueues;

    public RabbitMqConfig(
            @Value("${gls.rabbit.quorum-queues.enabled:false}") boolean quorumQueues) {
        this.quorumQueues = quorumQueues;
    }

    /** Visible for testing. */
    QueueBuilder durable(String name) {
        QueueBuilder builder = QueueBuilder.durable(name);
        if (quorumQueues) {
            builder.quorum();
        }
        return builder;
    }

    @Bean
    public TopicExchange documentExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public Queue classifiedQueue() {
        return durable(QUEUE_CLASSIFIED).build();
    }

    @Bean
    public Binding classifiedBinding() {
        return BindingBuilder.bind(classifiedQueue()).to(documentExchange()).with(ROUTING_CLASSIFIED);
    }

    @Bean
    public MessageConverter jsonMessageConverter(JsonMapper jsonMapper) {
        return new JacksonJsonMessageConverter(jsonMapper);
    }
}
