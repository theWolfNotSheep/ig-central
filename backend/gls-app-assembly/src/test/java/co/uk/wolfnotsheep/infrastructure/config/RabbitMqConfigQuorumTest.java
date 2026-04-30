package co.uk.wolfnotsheep.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Queue;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 2.3 PR1 — verify that the {@code gls.rabbit.quorum-queues.enabled}
 * flag actually toggles the {@code x-queue-type} argument on every queue
 * declared by {@link RabbitMqConfig}. Test exercises the topology in
 * isolation (no Spring context, no broker) by instantiating the config
 * directly with both flag values.
 */
class RabbitMqConfigQuorumTest {

    private static final String QUEUE_TYPE = "x-queue-type";
    private static final String QUORUM = "quorum";

    @Test
    void default_classic_queues_have_no_queue_type_argument() {
        RabbitMqConfig classic = new RabbitMqConfig(false);

        for (Queue q : queues(classic)) {
            assertThat(q.getArguments())
                    .as("queue %s should not declare x-queue-type when flag is off", q.getName())
                    .doesNotContainKey(QUEUE_TYPE);
        }
    }

    @Test
    void quorum_flag_marks_every_queue_as_quorum_type() {
        RabbitMqConfig quorum = new RabbitMqConfig(true);

        for (Queue q : queues(quorum)) {
            assertThat(q.getArguments())
                    .as("queue %s should be quorum when flag is on", q.getName())
                    .containsEntry(QUEUE_TYPE, QUORUM);
        }
    }

    @Test
    void DLQ_arguments_remain_intact_in_quorum_mode() {
        RabbitMqConfig quorum = new RabbitMqConfig(true);

        // Pipeline queues all have DLX wiring on the platform exchange.
        Queue llmJobs = quorum.llmJobsQueue();
        assertThat(llmJobs.getArguments()).containsEntry("x-dead-letter-exchange",
                RabbitMqConfig.PIPELINE_EXCHANGE);
        assertThat(llmJobs.getArguments()).containsEntry("x-dead-letter-routing-key",
                "pipeline.dlq");
        assertThat(llmJobs.getArguments()).containsEntry(QUEUE_TYPE, QUORUM);

        // Document queues use the document DLX.
        Queue ingested = quorum.ingestedQueue();
        assertThat(ingested.getArguments()).containsEntry("x-dead-letter-exchange",
                RabbitMqConfig.DLX_EXCHANGE);
        assertThat(ingested.getArguments()).containsEntry(QUEUE_TYPE, QUORUM);
    }

    @Test
    void durable_helper_returns_a_quorum_or_classic_builder_per_flag() {
        // The helper is package-private — exercise it directly to lock in the contract.
        RabbitMqConfig quorum = new RabbitMqConfig(true);
        Queue qb = quorum.durable("test-quorum").build();
        assertThat(qb.getArguments()).containsEntry(QUEUE_TYPE, QUORUM);

        RabbitMqConfig classic = new RabbitMqConfig(false);
        Queue cb = classic.durable("test-classic").build();
        assertThat(cb.getArguments()).doesNotContainKey(QUEUE_TYPE);
    }

    private static java.util.List<Queue> queues(RabbitMqConfig cfg) {
        return java.util.List.of(
                cfg.deadLetterQueue(),
                cfg.ingestedQueue(),
                cfg.processedQueue(),
                cfg.classifiedQueue(),
                cfg.llmJobsQueue(),
                cfg.llmCompletedQueue(),
                cfg.pipelineResumeQueue(),
                cfg.pipelineDlq());
    }
}
