package co.uk.wolfnotsheep.infrastructure.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import tools.jackson.databind.json.JsonMapper;

/**
 * RabbitMQ topology for the document and pipeline channels.
 *
 * <p><strong>Phase 2.3 — quorum-queue opt-in.</strong> Every queue
 * declared here can be created as a quorum queue when
 * {@code gls.rabbit.quorum-queues.enabled=true}. Quorum queues are
 * Raft-replicated, survive node loss in a multi-node RabbitMQ cluster
 * with no message loss, and are the production-recommended type. The
 * flag defaults to {@code false} for backward compatibility — existing
 * deployments have classic queues and changing the type requires
 * deleting and recreating each queue (RabbitMQ refuses a declaration
 * that contradicts an existing queue's type). Operators flip the flag
 * during a planned maintenance window with the matching
 * delete-then-redeploy runbook.
 */
@Configuration
public class RabbitMqConfig {

    public static final String EXCHANGE = "gls.documents";
    public static final String DLX_EXCHANGE = "gls.documents.dlx";

    public static final String QUEUE_INGESTED = "gls.documents.ingested";
    public static final String QUEUE_PROCESSED = "gls.documents.processed";
    public static final String QUEUE_CLASSIFIED = "gls.documents.classified";
    public static final String QUEUE_DLQ = "gls.documents.dlq";

    // ── Pipeline async queues ───────────────────────────
    public static final String PIPELINE_EXCHANGE = "gls.pipeline";
    public static final String QUEUE_LLM_JOBS = "gls.pipeline.llm.jobs";
    public static final String QUEUE_LLM_COMPLETED = "gls.pipeline.llm.completed";
    public static final String QUEUE_PIPELINE_RESUME = "gls.pipeline.resume";
    public static final String QUEUE_PIPELINE_DLQ = "gls.pipeline.dlq";

    public static final String ROUTING_INGESTED = "document.ingested";
    public static final String ROUTING_PROCESSED = "document.processed";
    public static final String ROUTING_CLASSIFIED = "document.classified";

    public static final String ROUTING_LLM_JOB_REQUESTED = "pipeline.llm.requested";
    public static final String ROUTING_LLM_JOB_COMPLETED = "pipeline.llm.completed";
    public static final String ROUTING_PIPELINE_RESUME = "pipeline.resume";

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

    /** Dead-letter exchange — poison messages and exhausted retries land here. */
    @Bean
    public FanoutExchange deadLetterExchange() {
        return new FanoutExchange(DLX_EXCHANGE, true, false);
    }

    /** Dead-letter queue — visible in monitoring for admin review. */
    @Bean
    public Queue deadLetterQueue() {
        return durable(QUEUE_DLQ).build();
    }

    @Bean
    public Binding deadLetterBinding() {
        return BindingBuilder.bind(deadLetterQueue()).to(deadLetterExchange());
    }

    @Bean
    public Queue ingestedQueue() {
        return durable(QUEUE_INGESTED)
                .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
                .build();
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

    // ── Pipeline exchange and queues ────────────────────

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
    public Queue pipelineResumeQueue() {
        return durable(QUEUE_PIPELINE_RESUME)
                .withArgument("x-dead-letter-exchange", PIPELINE_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", "pipeline.dlq")
                .build();
    }

    @Bean
    public Queue pipelineDlq() {
        return durable(QUEUE_PIPELINE_DLQ).build();
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
    public Binding pipelineResumeBinding() {
        return BindingBuilder.bind(pipelineResumeQueue()).to(pipelineExchange()).with(ROUTING_PIPELINE_RESUME);
    }

    @Bean
    public Binding pipelineDlqBinding() {
        return BindingBuilder.bind(pipelineDlq()).to(pipelineExchange()).with("pipeline.dlq");
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
        factory.setPrefetchCount(5); // Limit prefetch to prevent overwhelming LLM/Ollama
        return factory;
    }
}
