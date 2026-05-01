package co.uk.wolfnotsheep.docprocessing.config;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Queue;

import static org.assertj.core.api.Assertions.assertThat;

class RabbitMqConfigQuorumTest {

    private static final String QUEUE_TYPE = "x-queue-type";
    private static final String QUORUM = "quorum";

    @Test
    void classic_default_no_x_queue_type_argument() {
        RabbitMqConfig classic = new RabbitMqConfig(false);
        assertThat(classic.ingestedQueue().getArguments()).doesNotContainKey(QUEUE_TYPE);
        assertThat(classic.processedQueue().getArguments()).doesNotContainKey(QUEUE_TYPE);
    }

    @Test
    void quorum_flag_marks_every_queue_as_quorum_type() {
        RabbitMqConfig quorum = new RabbitMqConfig(true);
        assertThat(quorum.ingestedQueue().getArguments()).containsEntry(QUEUE_TYPE, QUORUM);
        assertThat(quorum.processedQueue().getArguments()).containsEntry(QUEUE_TYPE, QUORUM);
    }
}
