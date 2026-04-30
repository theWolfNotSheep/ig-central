package co.uk.wolfnotsheep.indexing.consumer;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/**
 * Indexing worker's own queue on the existing {@code gls.documents}
 * topic exchange, bound to the {@code document.classified} routing
 * key. Each consumer of {@code document.classified} (enforcement,
 * pipeline-execution, indexing) binds its own queue so the broker
 * fans out the event to all of them — see
 * {@code contracts/messaging/asyncapi.yaml#/operations/consumeDocumentClassified}.
 *
 * <p>Phase 2.3 — supports {@code gls.rabbit.quorum-queues.enabled=true}
 * for Raft-replicated queues. See {@code gls-app-assembly}'s
 * {@code RabbitMqConfig} for the migration runbook.
 */
@Configuration
public class RabbitMqConfig {

    public static final String EXCHANGE = "gls.documents";
    public static final String QUEUE_CLASSIFIED = "gls.documents.classified.indexing";
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
    public Queue indexingClassifiedQueue() {
        return durable(QUEUE_CLASSIFIED).build();
    }

    @Bean
    public Binding indexingClassifiedBinding() {
        return BindingBuilder.bind(indexingClassifiedQueue())
                .to(documentExchange())
                .with(ROUTING_CLASSIFIED);
    }

    @Bean
    public MessageConverter jsonMessageConverter(JsonMapper jsonMapper) {
        return new JacksonJsonMessageConverter(jsonMapper);
    }
}
