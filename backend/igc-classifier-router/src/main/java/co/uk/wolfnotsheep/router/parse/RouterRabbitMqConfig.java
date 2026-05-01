package co.uk.wolfnotsheep.router.parse;

import org.springframework.amqp.core.AnonymousQueue;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;

/**
 * Rabbit topology for the router's LLM dispatch path. Activated when
 * {@code igc.router.cascade.llm.enabled=true}; without that flag the
 * mock cascade stays in place.
 *
 * <p>The exchange + queue declarations are deliberately compatible
 * with the existing {@code igc-llm-orchestration} topology — the
 * exchange is shared (passive declare via Spring's idempotent
 * topology), the dispatch queue ({@code igc.pipeline.llm.jobs}) is
 * already created by the LLM worker, and we add a per-replica
 * non-durable, exclusive, auto-named queue for completion events so
 * we don't compete with {@code PipelineResumeConsumer}'s shared
 * durable queue.
 */
@Configuration
@ConditionalOnProperty(prefix = "igc.router.cascade.llm", name = "enabled", havingValue = "true")
public class RouterRabbitMqConfig {

    public static final String PIPELINE_EXCHANGE = "igc.pipeline";
    public static final String ROUTING_LLM_JOB_REQUESTED = "pipeline.llm.requested";
    public static final String ROUTING_LLM_JOB_COMPLETED = "pipeline.llm.completed";

    @Bean
    public TopicExchange routerPipelineExchange() {
        return new TopicExchange(PIPELINE_EXCHANGE, true, false);
    }

    /**
     * Per-replica completion queue. Auto-named, non-durable, exclusive
     * — created on listener attach, deleted when the connection drops.
     * Matches the {@code igc.config.changed} pattern used elsewhere
     * in the v2 stack.
     */
    @Bean
    public Queue routerLlmCompletedQueue() {
        return new AnonymousQueue();
    }

    @Bean
    public Binding routerLlmCompletedBinding(Queue routerLlmCompletedQueue,
                                             TopicExchange routerPipelineExchange) {
        return BindingBuilder.bind(routerLlmCompletedQueue)
                .to(routerPipelineExchange)
                .with(ROUTING_LLM_JOB_COMPLETED);
    }

    @Bean
    public LlmDispatchCascadeService llmDispatchCascadeService(
            org.springframework.amqp.rabbit.core.RabbitTemplate rabbitTemplate,
            @Value("${igc.router.cascade.llm.timeout:PT60S}") Duration timeout) {
        return new LlmDispatchCascadeService(
                rabbitTemplate, PIPELINE_EXCHANGE, ROUTING_LLM_JOB_REQUESTED, timeout);
    }

    /**
     * Jackson converter so {@code @RabbitListener(LlmJobResult)} can
     * deserialise the wire payload. Matches the converter the LLM
     * worker uses ({@code igc-llm-orchestration}'s
     * {@code RabbitMqConfig#jsonMessageConverter}).
     */
    @Bean
    public MessageConverter routerJsonMessageConverter(JsonMapper jsonMapper) {
        return new JacksonJsonMessageConverter(jsonMapper);
    }

    @Bean
    public SimpleRabbitListenerContainerFactory routerRabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter routerJsonMessageConverter) {
        var factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(routerJsonMessageConverter);
        factory.setDefaultRequeueRejected(false);
        factory.setPrefetchCount(8);
        return factory;
    }
}
