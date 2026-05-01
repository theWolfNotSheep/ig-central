package co.uk.wolfnotsheep.platformaudit.relay;

import co.uk.wolfnotsheep.platformaudit.envelope.Actor;
import co.uk.wolfnotsheep.platformaudit.envelope.AuditDetails;
import co.uk.wolfnotsheep.platformaudit.envelope.AuditEvent;
import co.uk.wolfnotsheep.platformaudit.envelope.Outcome;
import co.uk.wolfnotsheep.platformaudit.envelope.Resource;
import co.uk.wolfnotsheep.platformaudit.envelope.ResourceType;
import co.uk.wolfnotsheep.platformaudit.envelope.Tier;
import co.uk.wolfnotsheep.platformaudit.outbox.AuditOutboxRecord;
import co.uk.wolfnotsheep.platformaudit.outbox.AuditOutboxRepository;
import co.uk.wolfnotsheep.platformaudit.outbox.OutboxStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.Pageable;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit tests for {@link OutboxRelay} using mocked repository + RabbitTemplate. */
class OutboxRelayTest {

    private static final String VALID_ULID = "01HMQX9V3K5T7Z9N2B4D6F8H0J";

    private AuditOutboxRepository repository;
    private RabbitTemplate rabbitTemplate;
    private OutboxRelayMetrics metrics;
    private OutboxRelay relay;

    @BeforeEach
    void setUp() {
        repository = mock(AuditOutboxRepository.class);
        rabbitTemplate = mock(RabbitTemplate.class);
        metrics = new OutboxRelayMetrics(
                new io.micrometer.core.instrument.simple.SimpleMeterRegistry(),
                repository);
        relay = new OutboxRelay(repository, rabbitTemplate, defaultProperties(), metrics);
    }

    @Test
    void empty_batch_is_a_no_op() {
        when(repository.findByStatusAndNextRetryAtBeforeOrderByCreatedAtAsc(
                eq(OutboxStatus.PENDING), any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of());

        relay.pollOnce();

        verify(rabbitTemplate, never()).send(any(), any(), any(Message.class));
        verify(repository, never()).save(any());
    }

    @Test
    void disabled_relay_skips_polling_entirely() {
        OutboxRelay disabled = new OutboxRelay(
                repository, rabbitTemplate,
                new OutboxRelayProperties(false, null, 0, 0, null, null, null),
                metrics);

        disabled.pollOnce();

        verify(repository, never()).findByStatusAndNextRetryAtBeforeOrderByCreatedAtAsc(
                any(), any(), any());
    }

    @Test
    void system_tier_publishes_with_tier2_routing_and_marks_published() {
        AuditOutboxRecord row = pending(envelope(Tier.SYSTEM, AuditDetails.of(
                Map.of("k", "v"),
                Map.of("rawText", "operational detail"))));
        when(repository.findByStatusAndNextRetryAtBeforeOrderByCreatedAtAsc(
                any(), any(), any())).thenReturn(List.of(row));

        relay.pollOnce();

        verify(rabbitTemplate).send(eq("igc.audit"), eq("audit.tier2.DOCUMENT_CLASSIFIED"), any(Message.class));
        var saved = captureSaved();
        assertThat(saved.status()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(saved.publishedAt()).isNotNull();
        assertThat(saved.lastError()).isNull();
    }

    @Test
    void domain_tier_strips_content_to_sha256_and_publishes_with_tier1_routing() {
        AuditOutboxRecord row = pending(envelope(Tier.DOMAIN, AuditDetails.of(
                Map.of("categoryId", "HR-LEAVE"),
                Map.of("rawText", "private content"))));
        when(repository.findByStatusAndNextRetryAtBeforeOrderByCreatedAtAsc(
                any(), any(), any())).thenReturn(List.of(row));

        relay.pollOnce();

        var sent = capturePublishedMessage();
        verify(rabbitTemplate).send(eq("igc.audit"), eq("audit.tier1.DOCUMENT_CLASSIFIED"), any(Message.class));
        String body = new String(sent.getBody(), StandardCharsets.UTF_8);
        assertThat(body).contains("sha256:");
        assertThat(body).doesNotContain("private content");
        assertThat(sent.getMessageProperties().getMessageId()).isEqualTo(VALID_ULID);
        assertThat(sent.getMessageProperties().getContentType()).isEqualTo(MessageProperties.CONTENT_TYPE_JSON);
    }

    @Test
    void publish_failure_bumps_attempts_and_sets_next_retry() {
        AuditOutboxRecord row = pending(envelope(Tier.SYSTEM, AuditDetails.metadataOnly(Map.of("k", "v"))));
        when(repository.findByStatusAndNextRetryAtBeforeOrderByCreatedAtAsc(
                any(), any(), any())).thenReturn(List.of(row));
        doThrow(new AmqpException("broker is down"))
                .when(rabbitTemplate).send(any(), any(), any(Message.class));

        relay.pollOnce();

        var saved = captureSaved();
        assertThat(saved.status()).isEqualTo(OutboxStatus.PENDING);
        assertThat(saved.attempts()).isEqualTo(1);
        assertThat(saved.lastError()).contains("broker is down");
        assertThat(saved.nextRetryAt()).isAfter(row.nextRetryAt());
    }

    @Test
    void terminal_failure_marks_FAILED_after_max_attempts() {
        // Row has already been attempted maxAttempts - 1 times, so this run trips the cap.
        AuditOutboxRecord row = withAttempts(
                pending(envelope(Tier.SYSTEM, AuditDetails.metadataOnly(Map.of("k", "v")))),
                /* attempts */ 4);
        when(repository.findByStatusAndNextRetryAtBeforeOrderByCreatedAtAsc(
                any(), any(), any())).thenReturn(List.of(row));
        doThrow(new AmqpException("still down"))
                .when(rabbitTemplate).send(any(), any(), any(Message.class));

        relay.pollOnce();

        var saved = captureSaved();
        assertThat(saved.status()).isEqualTo(OutboxStatus.FAILED);
        assertThat(saved.attempts()).isEqualTo(5);
        assertThat(saved.lastError()).contains("gave up");
    }

    private OutboxRelayProperties defaultProperties() {
        return new OutboxRelayProperties(
                true,
                Duration.ofSeconds(5),
                50,
                5,
                Duration.ofSeconds(1),
                Duration.ofMinutes(5),
                "igc.audit");
    }

    private AuditOutboxRecord pending(AuditEvent envelope) {
        return AuditOutboxRecord.pendingFor(envelope);
    }

    private AuditOutboxRecord withAttempts(AuditOutboxRecord row, int attempts) {
        return new AuditOutboxRecord(
                row.id(), row.eventId(), row.tier(), row.eventType(),
                row.envelope(), row.status(), attempts, row.lastError(),
                row.createdAt(), row.publishedAt(), row.nextRetryAt());
    }

    private AuditEvent envelope(Tier tier, AuditDetails details) {
        Resource resource = tier == Tier.DOMAIN ? Resource.of(ResourceType.DOCUMENT, "doc_1") : null;
        String retentionClass = tier == Tier.DOMAIN ? "7Y" : null;
        String previousEventHash = tier == Tier.DOMAIN ? null : null;
        return new AuditEvent(
                VALID_ULID,
                "DOCUMENT_CLASSIFIED",
                tier,
                AuditEvent.CURRENT_SCHEMA_VERSION,
                Instant.parse("2026-04-26T22:00:00Z"),
                "doc_1", null, null, null,
                Actor.system("igc-app-assembly", "1.0.0", "pod-abc"),
                resource, "CLASSIFY", Outcome.SUCCESS,
                details, retentionClass, previousEventHash);
    }

    private AuditOutboxRecord captureSaved() {
        var captor = org.mockito.ArgumentCaptor.forClass(AuditOutboxRecord.class);
        verify(repository, times(1)).save(captor.capture());
        return captor.getValue();
    }

    private Message capturePublishedMessage() {
        var captor = org.mockito.ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate).send(any(), any(), captor.capture());
        return captor.getValue();
    }
}
