package co.uk.wolfnotsheep.enforcement.config;

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
    public static final String QUEUE_CLASSIFIED = "gls.documents.classified";
    public static final String ROUTING_CLASSIFIED = "document.classified";

    @Bean
    public TopicExchange documentExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public Queue classifiedQueue() {
        return QueueBuilder.durable(QUEUE_CLASSIFIED).build();
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
