package co.uk.wolfnotsheep.infrastructure.services;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DlqReplayServiceTest {

    private RabbitTemplate rabbitTemplate;
    private MeterRegistry meterRegistry;
    private DlqReplayService service;

    @BeforeEach
    void setUp() {
        rabbitTemplate = mock(RabbitTemplate.class);
        meterRegistry = new SimpleMeterRegistry();
        service = new DlqReplayService(rabbitTemplate, providerOf(meterRegistry),
                Set.of("gls.documents.dlq", "gls.pipeline.dlq"));
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<MeterRegistry> providerOf(MeterRegistry mr) {
        ObjectProvider<MeterRegistry> p = mock(ObjectProvider.class);
        when(p.getIfAvailable()).thenReturn(mr);
        return p;
    }

    private double counter(String queue, String outcome) {
        var c = meterRegistry.find("dlq.replay")
                .tags("queue", queue, "outcome", outcome).counter();
        return c == null ? 0.0 : c.count();
    }

    private static Message messageWithXDeath(String exchange, String routingKey) {
        MessageProperties props = new MessageProperties();
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("queue", "gls.documents.processed");
        entry.put("exchange", exchange);
        entry.put("routing-keys", List.of(routingKey));
        entry.put("count", 1L);
        entry.put("reason", "rejected");
        props.setHeader("x-death", List.of(entry));
        props.setHeader("x-first-death-exchange", exchange);
        props.setHeader("x-first-death-queue", "gls.documents.processed");
        props.setHeader("x-first-death-reason", "rejected");
        props.setContentType("application/json");
        return new Message("{}".getBytes(), props);
    }

    @Test
    void disallowed_queue_throws() {
        assertThatThrownBy(() -> service.replay("not.allowed", 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not in the allowed");
        verify(rabbitTemplate, never()).receive((String) org.mockito.ArgumentMatchers.any());
    }

    @Test
    void zero_or_negative_max_throws() {
        assertThatThrownBy(() -> service.replay("gls.documents.dlq", 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.replay("gls.documents.dlq", -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void empty_queue_returns_zero_counts() {
        when(rabbitTemplate.receive("gls.documents.dlq")).thenReturn(null);

        var result = service.replay("gls.documents.dlq", 10);

        assertThat(result.replayed()).isZero();
        assertThat(result.skipped()).isZero();
        verify(rabbitTemplate, never()).send(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(Message.class));
    }

    @Test
    void single_message_with_x_death_is_re_published_to_origin() {
        Message msg = messageWithXDeath("gls.documents", "document.processed");
        when(rabbitTemplate.receive("gls.documents.dlq"))
                .thenReturn(msg).thenReturn(null);

        var result = service.replay("gls.documents.dlq", 10);

        assertThat(result.replayed()).isEqualTo(1);
        assertThat(result.skipped()).isZero();

        ArgumentCaptor<Message> sentMessage = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate, times(1)).send(eq("gls.documents"), eq("document.processed"),
                sentMessage.capture());
        // x-death stripped from re-published message so the broker's dead-letter machinery resets.
        assertThat(sentMessage.getValue().getMessageProperties().getHeaders())
                .doesNotContainKey("x-death")
                .doesNotContainKey("x-first-death-exchange");
        assertThat(counter("gls.documents.dlq", "replayed")).isEqualTo(1.0);
    }

    @Test
    void max_messages_is_honoured() {
        Message msg = messageWithXDeath("gls.documents", "document.processed");
        when(rabbitTemplate.receive("gls.documents.dlq"))
                .thenReturn(msg, msg, msg, msg, null);

        var result = service.replay("gls.documents.dlq", 2);

        assertThat(result.replayed()).isEqualTo(2);
        // Only 2 calls to receive — we stop once max is reached.
        verify(rabbitTemplate, times(2)).receive("gls.documents.dlq");
        verify(rabbitTemplate, times(2)).send(eq("gls.documents"), eq("document.processed"),
                org.mockito.ArgumentMatchers.any(Message.class));
    }

    @Test
    void message_without_x_death_is_skipped_not_replayed() {
        MessageProperties props = new MessageProperties();
        Message msg = new Message("{}".getBytes(), props);
        when(rabbitTemplate.receive("gls.documents.dlq")).thenReturn(msg).thenReturn(null);

        var result = service.replay("gls.documents.dlq", 10);

        assertThat(result.replayed()).isZero();
        assertThat(result.skipped()).isEqualTo(1);
        verify(rabbitTemplate, never()).send(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(Message.class));
        assertThat(counter("gls.documents.dlq", "skipped")).isEqualTo(1.0);
    }

    @Test
    void send_failure_during_replay_does_not_break_the_batch() {
        Message msg = messageWithXDeath("gls.documents", "document.processed");
        when(rabbitTemplate.receive("gls.documents.dlq"))
                .thenReturn(msg, msg, null);
        org.mockito.Mockito.doThrow(new RuntimeException("broker down"))
                .doNothing()
                .when(rabbitTemplate).send(eq("gls.documents"), eq("document.processed"),
                        org.mockito.ArgumentMatchers.any(Message.class));

        var result = service.replay("gls.documents.dlq", 10);

        assertThat(result.replayed()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(1);
        assertThat(result.errors()).hasSize(1);
    }

    @Test
    void absent_MeterRegistry_does_not_break_replay() {
        DlqReplayService noMetrics = new DlqReplayService(rabbitTemplate, providerOf(null),
                Set.of("gls.documents.dlq"));
        Message msg = messageWithXDeath("gls.documents", "document.processed");
        when(rabbitTemplate.receive("gls.documents.dlq"))
                .thenReturn(msg).thenReturn(null);

        var result = noMetrics.replay("gls.documents.dlq", 10);

        assertThat(result.replayed()).isEqualTo(1);
    }
}
