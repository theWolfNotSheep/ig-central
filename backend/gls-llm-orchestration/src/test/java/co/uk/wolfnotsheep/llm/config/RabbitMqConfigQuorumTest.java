package co.uk.wolfnotsheep.llm.config;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Queue;

import static org.assertj.core.api.Assertions.assertThat;

class RabbitMqConfigQuorumTest {

    private static final String QUEUE_TYPE = "x-queue-type";
    private static final String QUORUM = "quorum";

    @Test
    void classic_default_no_x_queue_type_argument() {
        RabbitMqConfig classic = new RabbitMqConfig(false);
        for (Queue q : queues(classic)) {
            assertThat(q.getArguments()).doesNotContainKey(QUEUE_TYPE);
        }
    }

    @Test
    void quorum_flag_marks_every_queue_as_quorum_type() {
        RabbitMqConfig quorum = new RabbitMqConfig(true);
        for (Queue q : queues(quorum)) {
            assertThat(q.getArguments()).containsEntry(QUEUE_TYPE, QUORUM);
        }
    }

    @Test
    void DLQ_arguments_remain_intact_in_quorum_mode() {
        RabbitMqConfig quorum = new RabbitMqConfig(true);
        Queue processed = quorum.processedQueue();
        assertThat(processed.getArguments()).containsEntry("x-dead-letter-exchange",
                RabbitMqConfig.DLX_EXCHANGE);
        assertThat(processed.getArguments()).containsEntry(QUEUE_TYPE, QUORUM);

        Queue llmJobs = quorum.llmJobsQueue();
        assertThat(llmJobs.getArguments()).containsEntry("x-dead-letter-routing-key", "pipeline.dlq");
        assertThat(llmJobs.getArguments()).containsEntry(QUEUE_TYPE, QUORUM);
    }

    private static java.util.List<Queue> queues(RabbitMqConfig cfg) {
        return java.util.List.of(
                cfg.deadLetterQueue(),
                cfg.processedQueue(),
                cfg.classifiedQueue(),
                cfg.failedQueue(),
                cfg.llmJobsQueue(),
                cfg.llmCompletedQueue());
    }
}
