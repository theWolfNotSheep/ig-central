package co.uk.wolfnotsheep.infrastructure.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import tools.jackson.databind.json.JsonMapper;

@Configuration
public class RabbitMqConfig {

    public static final String EXCHANGE = "gls.documents";
    public static final String DLX_EXCHANGE = "gls.documents.dlx";

    public static final String QUEUE_INGESTED = "gls.documents.ingested";
    public static final String QUEUE_PROCESSED = "gls.documents.processed";
    public static final String QUEUE_CLASSIFIED = "gls.documents.classified";
    public static final String QUEUE_DLQ = "gls.documents.dlq";

    public static final String ROUTING_INGESTED = "document.ingested";
    public static final String ROUTING_PROCESSED = "document.processed";
    public static final String ROUTING_CLASSIFIED = "document.classified";

    @Bean
    public TopicExchange documentExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    /** Dead-letter exchange — poison messages and exhausted retries land here. */
    @Bean
    public FanoutExchange deadLetterExchange() {
        return new FanoutExchange(DLX_EXCHANGE, true, false);
    }

    /** Dead-letter queue — visible in monitoring for admin review. */
    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(QUEUE_DLQ).build();
    }

    @Bean
    public Binding deadLetterBinding() {
        return BindingBuilder.bind(deadLetterQueue()).to(deadLetterExchange());
    }

    @Bean
    public Queue ingestedQueue() {
        return QueueBuilder.durable(QUEUE_INGESTED)
                .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
                .build();
    }

    @Bean
    public Queue processedQueue() {
        return QueueBuilder.durable(QUEUE_PROCESSED)
                .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
                .build();
    }

    @Bean
    public Queue classifiedQueue() {
        return QueueBuilder.durable(QUEUE_CLASSIFIED)
                .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
                .build();
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
    public Binding classifiedBinding() {
        return BindingBuilder.bind(classifiedQueue()).to(documentExchange()).with(ROUTING_CLASSIFIED);
    }

    @Bean
    public MessageConverter jsonMessageConverter(JsonMapper jsonMapper) {
        return new JacksonJsonMessageConverter(jsonMapper);
    }

    /**
     * Prevent infinite requeue on consumer exceptions.
     * Failed messages are rejected and routed to the dead-letter queue (DLQ)
     * via the x-dead-letter-exchange configured on each queue.
     */
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
        var factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter);
        factory.setDefaultRequeueRejected(false);
        return factory;
    }
}
