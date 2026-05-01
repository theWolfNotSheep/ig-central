package co.uk.wolfnotsheep.docprocessing.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/**
 * RabbitMQ topology for the legacy in-process document-processing path.
 *
 * <p>Phase 2.3 — supports {@code igc.rabbit.quorum-queues.enabled=true}
 * for Raft-replicated queues. See {@code igc-app-assembly}'s
 * {@code RabbitMqConfig} for the migration runbook.
 */
@Configuration
@ConditionalOnProperty(name = "pipeline.execution-engine.enabled", havingValue = "false", matchIfMissing = true)
public class RabbitMqConfig {

    public static final String EXCHANGE = "igc.documents";

    public static final String QUEUE_INGESTED = "igc.documents.ingested";
    public static final String QUEUE_PROCESSED = "igc.documents.processed";

    public static final String ROUTING_INGESTED = "document.ingested";
    public static final String ROUTING_PROCESSED = "document.processed";

    private final boolean quorumQueues;

    public RabbitMqConfig(
            @Value("${igc.rabbit.quorum-queues.enabled:false}") boolean quorumQueues) {
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
    public Queue ingestedQueue() {
        return durable(QUEUE_INGESTED).build();
    }

    @Bean
    public Queue processedQueue() {
        return durable(QUEUE_PROCESSED).build();
    }

    @Bean
    public Binding ingestedBinding() {
        return BindingBuilder.bind(ingestedQueue()).to(documentExchange()).with(ROUTING_INGESTED);
    }

    @Bean
    public Binding processedBinding() {
        return BindingBuilder.bind(processedQueue()).to(documentExchange()).with(ROUTING_PROCESSED);
    }

    @Bean
    public MessageConverter jsonMessageConverter(JsonMapper jsonMapper) {
        return new JacksonJsonMessageConverter(jsonMapper);
    }
}
