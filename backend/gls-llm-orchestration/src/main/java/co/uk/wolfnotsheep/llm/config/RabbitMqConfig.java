package co.uk.wolfnotsheep.llm.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ topology for the document processing pipeline.
 *
 * Flow:
 *   document.ingested → [extraction workers]
 *   document.processed → [LLM classification — this service]
 *   document.classified → [governance enforcement, notifications]
 *   document.classification.failed → [dead letter / retry]
 */
@Configuration
public class RabbitMqConfig {

    public static final String EXCHANGE = "gls.documents";

    public static final String QUEUE_PROCESSED = "gls.documents.processed";
    public static final String QUEUE_CLASSIFIED = "gls.documents.classified";
    public static final String QUEUE_FAILED = "gls.documents.classification.failed";

    public static final String ROUTING_PROCESSED = "document.processed";
    public static final String ROUTING_CLASSIFIED = "document.classified";
    public static final String ROUTING_FAILED = "document.classification.failed";

    @Bean
    public TopicExchange documentExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public Queue processedQueue() {
        return QueueBuilder.durable(QUEUE_PROCESSED)
                .withArgument("x-dead-letter-exchange", EXCHANGE)
                .withArgument("x-dead-letter-routing-key", ROUTING_FAILED)
                .build();
    }

    @Bean
    public Queue classifiedQueue() {
        return QueueBuilder.durable(QUEUE_CLASSIFIED).build();
    }

    @Bean
    public Queue failedQueue() {
        return QueueBuilder.durable(QUEUE_FAILED).build();
    }

    @Bean
    public Binding processedBinding() {
        return BindingBuilder.bind(processedQueue()).to(documentExchange()).with(ROUTING_PROCESSED);
    }

    @Bean
    public Binding classifiedBinding() {
        return BindingBuilder.bind(classifiedQueue()).to(documentExchange()).with(ROUTING_CLASSIFIED);
    }

    @Bean
    public Binding failedBinding() {
        return BindingBuilder.bind(failedQueue()).to(documentExchange()).with(ROUTING_FAILED);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
