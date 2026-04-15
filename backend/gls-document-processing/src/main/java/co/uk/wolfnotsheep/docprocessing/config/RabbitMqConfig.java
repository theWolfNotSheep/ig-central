package co.uk.wolfnotsheep.docprocessing.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

@Configuration
@ConditionalOnProperty(name = "pipeline.execution-engine.enabled", havingValue = "false", matchIfMissing = true)
public class RabbitMqConfig {

    public static final String EXCHANGE = "gls.documents";

    public static final String QUEUE_INGESTED = "gls.documents.ingested";
    public static final String QUEUE_PROCESSED = "gls.documents.processed";

    public static final String ROUTING_INGESTED = "document.ingested";
    public static final String ROUTING_PROCESSED = "document.processed";

    @Bean
    public TopicExchange documentExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public Queue ingestedQueue() {
        return QueueBuilder.durable(QUEUE_INGESTED).build();
    }

    @Bean
    public Queue processedQueue() {
        return QueueBuilder.durable(QUEUE_PROCESSED).build();
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
