package co.uk.wolfnotsheep.llm.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/**
 * RabbitMQ topology for the LLM classification worker.
 * All queues route rejected/failed messages to the dead-letter exchange.
 */
@Configuration
public class RabbitMqConfig {

    public static final String EXCHANGE = "gls.documents";
    public static final String DLX_EXCHANGE = "gls.documents.dlx";

    public static final String QUEUE_PROCESSED = "gls.documents.processed";
    public static final String QUEUE_CLASSIFIED = "gls.documents.classified";
    public static final String QUEUE_FAILED = "gls.documents.classification.failed";
    public static final String QUEUE_DLQ = "gls.documents.dlq";

    public static final String ROUTING_PROCESSED = "document.processed";
    public static final String ROUTING_CLASSIFIED = "document.classified";
    public static final String ROUTING_FAILED = "document.classification.failed";

    @Bean
    public TopicExchange documentExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public FanoutExchange deadLetterExchange() {
        return new FanoutExchange(DLX_EXCHANGE, true, false);
    }

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(QUEUE_DLQ).build();
    }

    @Bean
    public Binding deadLetterBinding() {
        return BindingBuilder.bind(deadLetterQueue()).to(deadLetterExchange());
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
    public MessageConverter jsonMessageConverter(JsonMapper jsonMapper) {
        return new JacksonJsonMessageConverter(jsonMapper);
    }

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
