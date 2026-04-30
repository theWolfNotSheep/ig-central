package co.uk.wolfnotsheep.llm.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/**
 * RabbitMQ topology for the LLM classification worker.
 * All queues route rejected/failed messages to the dead-letter exchange.
 *
 * <p>Phase 2.3 — supports {@code gls.rabbit.quorum-queues.enabled=true}
 * for Raft-replicated queues. See the analogous config in
 * {@code gls-app-assembly} for the migration runbook.
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

    // ── Pipeline async queues ───────────────────────────
    public static final String PIPELINE_EXCHANGE = "gls.pipeline";
    public static final String QUEUE_LLM_JOBS = "gls.pipeline.llm.jobs";
    public static final String QUEUE_LLM_COMPLETED = "gls.pipeline.llm.completed";
    public static final String ROUTING_LLM_JOB_REQUESTED = "pipeline.llm.requested";
    public static final String ROUTING_LLM_JOB_COMPLETED = "pipeline.llm.completed";

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
    public FanoutExchange deadLetterExchange() {
        return new FanoutExchange(DLX_EXCHANGE, true, false);
    }

    @Bean
    public Queue deadLetterQueue() {
        return durable(QUEUE_DLQ).build();
    }

    @Bean
    public Binding deadLetterBinding() {
        return BindingBuilder.bind(deadLetterQueue()).to(deadLetterExchange());
    }

    @Bean
    public Queue processedQueue() {
        return durable(QUEUE_PROCESSED)
                .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
                .build();
    }

    @Bean
    public Queue classifiedQueue() {
        return durable(QUEUE_CLASSIFIED)
                .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
                .build();
    }

    @Bean
    public Queue failedQueue() {
        return durable(QUEUE_FAILED).build();
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

    // ── Pipeline exchange and LLM job queues ────────────

    @Bean
    public TopicExchange pipelineExchange() {
        return new TopicExchange(PIPELINE_EXCHANGE, true, false);
    }

    @Bean
    public Queue llmJobsQueue() {
        return durable(QUEUE_LLM_JOBS)
                .withArgument("x-dead-letter-exchange", PIPELINE_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", "pipeline.dlq")
                .build();
    }

    @Bean
    public Queue llmCompletedQueue() {
        return durable(QUEUE_LLM_COMPLETED)
                .withArgument("x-dead-letter-exchange", PIPELINE_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", "pipeline.dlq")
                .build();
    }

    @Bean
    public Binding llmJobsBinding() {
        return BindingBuilder.bind(llmJobsQueue()).to(pipelineExchange()).with(ROUTING_LLM_JOB_REQUESTED);
    }

    @Bean
    public Binding llmCompletedBinding() {
        return BindingBuilder.bind(llmCompletedQueue()).to(pipelineExchange()).with(ROUTING_LLM_JOB_COMPLETED);
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
        factory.setPrefetchCount(2); // Limit prefetch — LLM calls are slow, avoid overwhelming Ollama
        return factory;
    }
}
