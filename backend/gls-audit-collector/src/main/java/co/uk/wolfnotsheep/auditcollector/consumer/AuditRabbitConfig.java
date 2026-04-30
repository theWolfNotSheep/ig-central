package co.uk.wolfnotsheep.auditcollector.consumer;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/**
 * Topology for the audit collector. Two queues bound to the existing
 * {@code gls.audit} topic exchange — see
 * {@code contracts/audit/asyncapi.yaml} for the channel declarations
 * and {@code OutboxRelayProperties} for the producer-side defaults.
 *
 * <p>Tier 1 has a single queue (single-writer per CSV #4) — when we
 * eventually scale to multiple collector replicas, ShedLock leader
 * election keeps only one consuming. Tier 2 has its own queue;
 * horizontal scaling is fine because there's no chain ordering.
 */
@Configuration
public class AuditRabbitConfig {

    public static final String EXCHANGE = "gls.audit";
    public static final String QUEUE_TIER1 = "gls.audit.tier1.collector";
    public static final String QUEUE_TIER2 = "gls.audit.tier2.collector";
    public static final String ROUTING_TIER1 = "audit.tier1.*";
    public static final String ROUTING_TIER2 = "audit.tier2.*";

    @Bean
    public TopicExchange auditExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public Queue tier1Queue() {
        return QueueBuilder.durable(QUEUE_TIER1).build();
    }

    @Bean
    public Queue tier2Queue() {
        return QueueBuilder.durable(QUEUE_TIER2).build();
    }

    @Bean
    public Binding tier1Binding() {
        return BindingBuilder.bind(tier1Queue()).to(auditExchange()).with(ROUTING_TIER1);
    }

    @Bean
    public Binding tier2Binding() {
        return BindingBuilder.bind(tier2Queue()).to(auditExchange()).with(ROUTING_TIER2);
    }

    @Bean
    public MessageConverter auditMessageConverter(JsonMapper jsonMapper) {
        return new JacksonJsonMessageConverter(jsonMapper);
    }
}
