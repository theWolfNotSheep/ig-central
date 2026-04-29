package co.uk.wolfnotsheep.router.audit;

import co.uk.wolfnotsheep.platformaudit.envelope.ActorType;
import co.uk.wolfnotsheep.platformaudit.envelope.AuditEvent;
import co.uk.wolfnotsheep.platformaudit.envelope.EnvelopeValidator;
import co.uk.wolfnotsheep.platformaudit.envelope.Outcome;
import co.uk.wolfnotsheep.platformaudit.envelope.Tier;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RouterEventsTest {

    private final EnvelopeValidator validator = EnvelopeValidator.fromBundledSchema();

    @Test
    void completed_event_passes_schema_validation() {
        AuditEvent event = RouterEvents.completed(
                "gls-classifier-router", "0.0.1", "pod-abc",
                "node-1",
                "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01",
                "block-id-1", 3, "MOCK", 0.5f, 1024L, 200L);

        validator.validate(event);

        assertThat(event.eventType()).isEqualTo("CLASSIFY_COMPLETED");
        assertThat(event.tier()).isEqualTo(Tier.SYSTEM);
        assertThat(event.outcome()).isEqualTo(Outcome.SUCCESS);
        assertThat(event.action()).isEqualTo("CLASSIFY");
        assertThat(event.actor().type()).isEqualTo(ActorType.SYSTEM);
        assertThat(event.actor().service()).isEqualTo("gls-classifier-router");
        assertThat(event.details().metadata()).containsEntry("blockId", "block-id-1");
        assertThat(event.details().metadata()).containsEntry("blockVersion", 3);
        assertThat(event.details().metadata()).containsEntry("tierOfDecision", "MOCK");
        assertThat(event.details().metadata()).containsEntry("confidence", 0.5f);
    }

    @Test
    void completed_with_null_block_omits_those_fields() {
        AuditEvent event = RouterEvents.completed(
                "gls-classifier-router", "0.0.1", "pod-abc",
                "node-2", "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01",
                null, null, "MOCK", 0.5f, 0L, 1L);

        validator.validate(event);
        assertThat(event.details().metadata()).doesNotContainKey("blockId");
        assertThat(event.details().metadata()).doesNotContainKey("blockVersion");
    }

    @Test
    void failed_event_carries_errorCode_and_message() {
        AuditEvent event = RouterEvents.failed(
                "gls-classifier-router", "0.0.1", "pod-abc",
                "node-3", null,
                "ROUTER_BLOCK_NOT_FOUND", "block id 'unknown' not found");

        validator.validate(event);
        assertThat(event.eventType()).isEqualTo("CLASSIFY_FAILED");
        assertThat(event.outcome()).isEqualTo(Outcome.FAILURE);
        assertThat(event.details().metadata()).containsEntry("errorCode", "ROUTER_BLOCK_NOT_FOUND");
    }

    @Test
    void event_id_format_satisfies_envelope_pattern() {
        AuditEvent event = RouterEvents.completed(
                "gls-classifier-router", "0.0.1", "pod-abc",
                "node-x", "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01",
                "b", 1, "MOCK", 1.0f, 1L, 1L);
        assertThat(event.eventId()).matches("^[0-9A-HJKMNP-TV-Z]{26}$");
    }
}
