package co.uk.wolfnotsheep.infrastructure.services;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.GetResponse;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;

import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DlqReplayServiceTest {

    private RabbitTemplate rabbitTemplate;
    private MeterRegistry meterRegistry;
    private LockProvider lockProvider;
    private DlqReplayService service;
    private Channel channel;

    @BeforeEach
    void setUp() {
        rabbitTemplate = mock(RabbitTemplate.class);
        meterRegistry = new SimpleMeterRegistry();
        lockProvider = mock(LockProvider.class);
        SimpleLock lock = mock(SimpleLock.class);
        when(lockProvider.lock(any(LockConfiguration.class))).thenReturn(Optional.of(lock));
        channel = mock(Channel.class);
        service = new DlqReplayService(rabbitTemplate, providerOf(meterRegistry),
                lockProviderOf(lockProvider), Set.of("igc.documents.dlq", "igc.pipeline.dlq"));
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<MeterRegistry> providerOf(MeterRegistry mr) {
        ObjectProvider<MeterRegistry> p = mock(ObjectProvider.class);
        when(p.getIfAvailable()).thenReturn(mr);
        return p;
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<LockProvider> lockProviderOf(LockProvider lp) {
        ObjectProvider<LockProvider> p = mock(ObjectProvider.class);
        when(p.getIfAvailable()).thenReturn(lp);
        return p;
    }

    /**
     * Stub the rabbitTemplate.execute callback by giving it a queue of GetResponses
     * to return on successive basicGet calls; null at the end means queue empty.
     */
    @SuppressWarnings({"unchecked"})
    private void stubExecuteWithMessages(GetResponse... responses) throws Exception {
        Queue<GetResponse> q = new LinkedList<>();
        for (GetResponse r : responses) q.offer(r);
        q.offer(null);  // sentinel — empty queue
        when(channel.basicGet(anyString(), anyBoolean())).thenAnswer(inv -> q.poll());
        when(rabbitTemplate.execute(any())).thenAnswer(inv -> {
            org.springframework.amqp.rabbit.core.ChannelCallback<Object> cb =
                    (org.springframework.amqp.rabbit.core.ChannelCallback<Object>) inv.getArgument(0);
            return cb.doInRabbit(channel);
        });
    }

    private static GetResponse responseWithXDeath(long deliveryTag,
                                                  String exchange, String routingKey) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("queue", "igc.documents.processed");
        entry.put("exchange", exchange);
        entry.put("routing-keys", List.of(routingKey));
        entry.put("count", 1L);
        entry.put("reason", "rejected");
        Map<String, Object> headers = new LinkedHashMap<>();
        headers.put("x-death", List.of(entry));
        headers.put("x-first-death-exchange", exchange);
        headers.put("x-first-death-queue", "igc.documents.processed");
        headers.put("x-first-death-reason", "rejected");
        AMQP.BasicProperties props = new AMQP.BasicProperties.Builder()
                .contentType("application/json")
                .headers(headers)
                .build();
        Envelope envelope = new Envelope(deliveryTag, false, exchange, routingKey);
        return new GetResponse(envelope, props, "{}".getBytes(), 0);
    }

    private static GetResponse responseWithoutXDeath(long deliveryTag) {
        AMQP.BasicProperties props = new AMQP.BasicProperties.Builder()
                .contentType("application/json")
                .build();
        Envelope envelope = new Envelope(deliveryTag, false, "", "");
        return new GetResponse(envelope, props, "{}".getBytes(), 0);
    }

    private double counter(String queue, String outcome, String mode) {
        var c = meterRegistry.find("dlq.replay")
                .tags("queue", queue, "outcome", outcome, "mode", mode).counter();
        return c == null ? 0.0 : c.count();
    }

    @Test
    void disallowed_queue_throws() {
        assertThatThrownBy(() -> service.replay("not.allowed", 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not in the allowed");
    }

    @Test
    void zero_or_negative_max_throws() {
        assertThatThrownBy(() -> service.replay("igc.documents.dlq", 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void empty_queue_returns_zero_counts() throws Exception {
        stubExecuteWithMessages();

        var result = service.replay("igc.documents.dlq", 10);

        assertThat(result.replayed()).isZero();
        assertThat(result.skipped()).isZero();
        assertThat(result.dryRun()).isFalse();
    }

    @Test
    void real_mode_re_publishes_and_acks() throws Exception {
        stubExecuteWithMessages(responseWithXDeath(1L, "igc.documents", "document.processed"));

        var result = service.replay("igc.documents.dlq", 10);

        assertThat(result.replayed()).isEqualTo(1);
        assertThat(result.dryRun()).isFalse();
        verify(channel, times(1)).basicAck(eq(1L), eq(false));
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
        verify(rabbitTemplate, times(1)).send(eq("igc.documents"), eq("document.processed"), any());
        assertThat(counter("igc.documents.dlq", "replayed", "real")).isEqualTo(1.0);
    }

    @Test
    void dry_run_mode_does_not_publish_and_nacks_with_requeue() throws Exception {
        stubExecuteWithMessages(
                responseWithXDeath(1L, "igc.documents", "document.processed"),
                responseWithXDeath(2L, "igc.documents", "document.classified"));

        var result = service.dryRun("igc.documents.dlq", 10);

        assertThat(result.replayed()).isEqualTo(2);
        assertThat(result.dryRun()).isTrue();
        assertThat(result.preview()).hasSize(2);
        assertThat(result.preview().get(0).originExchange()).isEqualTo("igc.documents");
        assertThat(result.preview().get(0).originRoutingKey()).isEqualTo("document.processed");
        assertThat(result.preview().get(0).reason()).isEqualTo("rejected");
        verify(channel, times(2)).basicNack(anyLong(), eq(false), eq(true));
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
        verify(rabbitTemplate, never()).send(anyString(), anyString(), any());
        assertThat(counter("igc.documents.dlq", "replayed", "dry_run")).isEqualTo(2.0);
    }

    @Test
    void max_messages_honoured_in_real_mode() throws Exception {
        stubExecuteWithMessages(
                responseWithXDeath(1L, "igc.documents", "document.processed"),
                responseWithXDeath(2L, "igc.documents", "document.processed"),
                responseWithXDeath(3L, "igc.documents", "document.processed"));

        var result = service.replay("igc.documents.dlq", 2);

        assertThat(result.replayed()).isEqualTo(2);
        verify(rabbitTemplate, times(2)).send(eq("igc.documents"), eq("document.processed"), any());
    }

    @Test
    void message_without_x_death_is_skipped_and_acked_in_real_mode() throws Exception {
        stubExecuteWithMessages(responseWithoutXDeath(1L));

        var result = service.replay("igc.documents.dlq", 10);

        assertThat(result.replayed()).isZero();
        assertThat(result.skipped()).isEqualTo(1);
        verify(channel, times(1)).basicAck(eq(1L), eq(false));
        verify(rabbitTemplate, never()).send(anyString(), anyString(), any());
    }

    @Test
    void per_queue_lock_held_throws_ReplayInProgressException() {
        when(lockProvider.lock(any(LockConfiguration.class))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.replay("igc.documents.dlq", 10))
                .isInstanceOf(DlqReplayService.ReplayInProgressException.class);
        verify(rabbitTemplate, never()).execute(any());
    }

    @Test
    void absent_LockProvider_runs_without_lock() throws Exception {
        DlqReplayService noLock = new DlqReplayService(rabbitTemplate, providerOf(meterRegistry),
                lockProviderOf(null), Set.of("igc.documents.dlq"));
        stubExecuteWithMessages(responseWithXDeath(1L, "igc.documents", "document.processed"));

        var result = noLock.replay("igc.documents.dlq", 10);

        assertThat(result.replayed()).isEqualTo(1);
    }

    @Test
    void lock_is_released_after_replay_completes() throws Exception {
        SimpleLock lock = mock(SimpleLock.class);
        when(lockProvider.lock(any(LockConfiguration.class))).thenReturn(Optional.of(lock));
        stubExecuteWithMessages(responseWithXDeath(1L, "igc.documents", "document.processed"));

        service.replay("igc.documents.dlq", 10);

        verify(lock, times(1)).unlock();
    }

    @Test
    void absent_MeterRegistry_does_not_break_replay() throws Exception {
        DlqReplayService noMetrics = new DlqReplayService(rabbitTemplate, providerOf(null),
                lockProviderOf(lockProvider), Set.of("igc.documents.dlq"));
        stubExecuteWithMessages(responseWithXDeath(1L, "igc.documents", "document.processed"));

        var result = noMetrics.replay("igc.documents.dlq", 10);

        assertThat(result.replayed()).isEqualTo(1);
    }
}
