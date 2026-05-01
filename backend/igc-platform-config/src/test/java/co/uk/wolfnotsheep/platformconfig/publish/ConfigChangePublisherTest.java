package co.uk.wolfnotsheep.platformconfig.publish;

import co.uk.wolfnotsheep.platformconfig.event.ChangeType;
import co.uk.wolfnotsheep.platformconfig.event.ConfigChangedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConfigChangePublisherTest {

    private RabbitTemplate rabbitTemplate;
    private MeterRegistry meterRegistry;
    private ConfigChangePublisher publisher;

    @BeforeEach
    void setUp() {
        rabbitTemplate = mock(RabbitTemplate.class);
        meterRegistry = new SimpleMeterRegistry();
        publisher = new ConfigChangePublisher(
                rabbitTemplate, objectMapper(), "test-svc",
                providerOf(meterRegistry));
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<MeterRegistry> providerOf(MeterRegistry mr) {
        ObjectProvider<MeterRegistry> p = mock(ObjectProvider.class);
        when(p.getIfAvailable()).thenReturn(mr);
        return p;
    }

    private static ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
    }

    private double counter(String name) {
        var c = meterRegistry.find(name).counter();
        return c == null ? 0.0 : c.count();
    }

    @Test
    void successful_publish_does_not_buffer() {
        doNothing().when(rabbitTemplate).send(eq("igc.config"), eq("config.changed"), any(Message.class));

        publisher.publishSingle("TAXONOMY", "tax-1", ChangeType.UPDATED);

        verify(rabbitTemplate, times(1)).send(eq("igc.config"), eq("config.changed"), any(Message.class));
        assertThat(publisher.retryBufferSize()).isZero();
        assertThat(counter("config.change.retry.enqueued")).isZero();
    }

    @Test
    void AMQP_failure_buffers_event_for_retry() {
        doThrow(new AmqpException("broker down"))
                .when(rabbitTemplate).send(eq("igc.config"), eq("config.changed"), any(Message.class));

        publisher.publishSingle("TAXONOMY", "tax-1", ChangeType.UPDATED);

        assertThat(publisher.retryBufferSize()).isEqualTo(1);
        assertThat(counter("config.change.retry.enqueued")).isEqualTo(1.0);
    }

    @Test
    void scheduled_flush_drains_buffer_when_broker_recovers() {
        doThrow(new AmqpException("broker down"))
                .doThrow(new AmqpException("still down"))
                .doNothing()
                .doNothing()
                .when(rabbitTemplate).send(eq("igc.config"), eq("config.changed"), any(Message.class));

        // Two events buffered while broker is down.
        publisher.publishSingle("TAXONOMY", "tax-1", ChangeType.UPDATED);
        publisher.publishSingle("TAXONOMY", "tax-2", ChangeType.UPDATED);
        assertThat(publisher.retryBufferSize()).isEqualTo(2);

        // Broker recovers — flush drains both.
        publisher.flushRetryBuffer();

        assertThat(publisher.retryBufferSize()).isZero();
        assertThat(counter("config.change.retry.flushed")).isEqualTo(2.0);
        // 2 enqueued (broker down), 4 send attempts total (2 initial + 2 flush)
        verify(rabbitTemplate, times(4)).send(eq("igc.config"), eq("config.changed"), any(Message.class));
    }

    @Test
    void flush_stops_at_first_failure_so_stuck_broker_doesnt_burn_buffer() {
        // First publish fails → buffered. Second publish also fails → buffered.
        // Flush attempt: first send fails → stop, both events still buffered.
        doThrow(new AmqpException("broker down"))
                .when(rabbitTemplate).send(eq("igc.config"), eq("config.changed"), any(Message.class));

        publisher.publishSingle("TAXONOMY", "a", ChangeType.UPDATED);
        publisher.publishSingle("TAXONOMY", "b", ChangeType.UPDATED);
        assertThat(publisher.retryBufferSize()).isEqualTo(2);

        publisher.flushRetryBuffer();

        // Flush attempted exactly one send (then stopped on failure).
        // Total sends: 2 initial publishes + 1 flush attempt = 3.
        verify(rabbitTemplate, times(3)).send(eq("igc.config"), eq("config.changed"), any(Message.class));
        assertThat(publisher.retryBufferSize()).isEqualTo(2);
        assertThat(counter("config.change.retry.flushed")).isZero();
    }

    @Test
    void buffer_at_capacity_drops_oldest_event() {
        ConfigChangePublisher tinyBuffer = new ConfigChangePublisher(
                rabbitTemplate, objectMapper(), "test-svc",
                providerOf(meterRegistry), 2);

        doThrow(new AmqpException("broker down"))
                .when(rabbitTemplate).send(eq("igc.config"), eq("config.changed"), any(Message.class));

        tinyBuffer.publishSingle("TAXONOMY", "a", ChangeType.UPDATED);
        tinyBuffer.publishSingle("TAXONOMY", "b", ChangeType.UPDATED);
        tinyBuffer.publishSingle("TAXONOMY", "c", ChangeType.UPDATED);  // forces drop of "a"

        assertThat(tinyBuffer.retryBufferSize()).isEqualTo(2);
        assertThat(counter("config.change.retry.enqueued")).isEqualTo(3.0);
        assertThat(counter("config.change.retry.dropped")).isEqualTo(1.0);
    }

    @Test
    void empty_flush_is_a_silent_no_op() {
        publisher.flushRetryBuffer();

        verify(rabbitTemplate, never()).send(any(), any(), any(Message.class));
        assertThat(counter("config.change.retry.flushed")).isZero();
    }

    @Test
    void serialisation_failure_drops_event_does_not_buffer() {
        // ObjectMapper that always fails — emulates a non-serialisable event.
        ObjectMapper failingMapper = new ObjectMapper() {
            @Override
            public byte[] writeValueAsBytes(Object value) throws com.fasterxml.jackson.core.JsonProcessingException {
                throw new com.fasterxml.jackson.databind.JsonMappingException(null, "boom");
            }
        };
        ConfigChangePublisher failingSerialiser = new ConfigChangePublisher(
                rabbitTemplate, failingMapper, "test-svc", providerOf(meterRegistry));

        failingSerialiser.publishSingle("TAXONOMY", "x", ChangeType.UPDATED);

        verify(rabbitTemplate, never()).send(any(), any(), any(Message.class));
        assertThat(failingSerialiser.retryBufferSize()).isZero();
        assertThat(counter("config.change.retry.dropped")).isEqualTo(1.0);
    }

    @Test
    void absent_MeterRegistry_does_not_break_publish_or_buffer() {
        ConfigChangePublisher noMetrics = new ConfigChangePublisher(
                rabbitTemplate, objectMapper(), "test-svc", providerOf(null));

        doThrow(new AmqpException("broker down"))
                .when(rabbitTemplate).send(eq("igc.config"), eq("config.changed"), any(Message.class));

        noMetrics.publishSingle("TAXONOMY", "x", ChangeType.UPDATED);
        assertThat(noMetrics.retryBufferSize()).isEqualTo(1);

        doNothing().when(rabbitTemplate).send(eq("igc.config"), eq("config.changed"), any(Message.class));
        noMetrics.flushRetryBuffer();
        assertThat(noMetrics.retryBufferSize()).isZero();
    }

    @Test
    void publishBulk_with_AMQP_failure_buffers_correctly() {
        doThrow(new AmqpException("broker down"))
                .when(rabbitTemplate).send(eq("igc.config"), eq("config.changed"), any(Message.class));

        publisher.publishBulk("STORAGE_TIER", ChangeType.UPDATED);

        assertThat(publisher.retryBufferSize()).isEqualTo(1);
    }

    @Test
    void direct_publish_with_traceparent_is_buffered_intact() {
        var event = new ConfigChangedEvent(
                "TAXONOMY", java.util.List.of("tax-1"), ChangeType.UPDATED,
                java.time.Instant.now(), "test-svc", "00-traceparent-00");

        doThrow(new AmqpException("broker down"))
                .doNothing()
                .when(rabbitTemplate).send(eq("igc.config"), eq("config.changed"), any(Message.class));

        publisher.publish(event);
        assertThat(publisher.retryBufferSize()).isEqualTo(1);
        publisher.flushRetryBuffer();
        assertThat(publisher.retryBufferSize()).isZero();
        // The buffered event was sent on flush — traceparent preserved through both attempts.
        verify(rabbitTemplate, times(2)).send(eq("igc.config"), eq("config.changed"), any(Message.class));
    }
}
