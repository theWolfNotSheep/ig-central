package co.uk.wolfnotsheep.indexing.consumer;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RabbitMqConfigQuorumTest {

    private static final String QUEUE_TYPE = "x-queue-type";
    private static final String QUORUM = "quorum";

    @Test
    void classic_default_no_x_queue_type_argument() {
        RabbitMqConfig classic = new RabbitMqConfig(false);
        assertThat(classic.indexingClassifiedQueue().getArguments()).doesNotContainKey(QUEUE_TYPE);
    }

    @Test
    void quorum_flag_marks_queue_as_quorum_type() {
        RabbitMqConfig quorum = new RabbitMqConfig(true);
        assertThat(quorum.indexingClassifiedQueue().getArguments()).containsEntry(QUEUE_TYPE, QUORUM);
    }
}
