package co.uk.wolfnotsheep.infrastructure.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {

    public static final String EXCHANGE = "gls.documents";
    public static final String QUEUE_INGESTED = "gls.documents.ingested";
    public static final String ROUTING_INGESTED = "document.ingested";

    @Bean
    public TopicExchange documentExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public Queue ingestedQueue() {
        return QueueBuilder.durable(QUEUE_INGESTED).build();
    }

    @Bean
    public Binding ingestedBinding() {
        return BindingBuilder.bind(ingestedQueue()).to(documentExchange()).with(ROUTING_INGESTED);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
