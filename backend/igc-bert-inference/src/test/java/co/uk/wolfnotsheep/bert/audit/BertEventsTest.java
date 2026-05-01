package co.uk.wolfnotsheep.bert.audit;

import co.uk.wolfnotsheep.platformaudit.envelope.ActorType;
import co.uk.wolfnotsheep.platformaudit.envelope.AuditEvent;
import co.uk.wolfnotsheep.platformaudit.envelope.EnvelopeValidator;
import co.uk.wolfnotsheep.platformaudit.envelope.Outcome;
import co.uk.wolfnotsheep.platformaudit.envelope.Tier;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BertEventsTest {

    private final EnvelopeValidator validator = EnvelopeValidator.fromBundledSchema();

    @Test
    void completed_event_passes_schema_validation() {
        AuditEvent event = BertEvents.completed(
                "igc-bert-inference", "0.0.1", "pod-abc",
                "node-1",
                "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01",
                "block-1", 3, "2026.04.0",
                "hr_letter", 0.94f, 1024L, 18L);

        validator.validate(event);
        assertThat(event.eventType()).isEqualTo("INFER_COMPLETED");
        assertThat(event.tier()).isEqualTo(Tier.SYSTEM);
        assertThat(event.outcome()).isEqualTo(Outcome.SUCCESS);
        assertThat(event.action()).isEqualTo("INFER");
        assertThat(event.actor().type()).isEqualTo(ActorType.SYSTEM);
        assertThat(event.actor().service()).isEqualTo("igc-bert-inference");
        assertThat(event.details().metadata()).containsEntry("blockId", "block-1");
        assertThat(event.details().metadata()).containsEntry("modelVersion", "2026.04.0");
        assertThat(event.details().metadata()).containsEntry("label", "hr_letter");
        assertThat(event.details().metadata()).containsEntry("confidence", 0.94f);
    }

    @Test
    void completed_event_omits_null_block_fields() {
        AuditEvent event = BertEvents.completed(
                "igc-bert-inference", "0.0.1", "pod-abc",
                null,
                "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01",
                null, null, null, null, null, 0L, 0L);

        validator.validate(event);
        assertThat(event.details().metadata()).doesNotContainKey("blockId");
        assertThat(event.details().metadata()).doesNotContainKey("blockVersion");
        assertThat(event.details().metadata()).doesNotContainKey("modelVersion");
        assertThat(event.details().metadata()).doesNotContainKey("label");
    }

    @Test
    void failed_event_carries_errorCode_and_message() {
        AuditEvent event = BertEvents.failed(
                "igc-bert-inference", "0.0.1", "pod-abc",
                "node-3", null,
                "MODEL_NOT_LOADED", "no BERT model is loaded on this replica");

        validator.validate(event);
        assertThat(event.eventType()).isEqualTo("INFER_FAILED");
        assertThat(event.outcome()).isEqualTo(Outcome.FAILURE);
        assertThat(event.details().metadata()).containsEntry("errorCode", "MODEL_NOT_LOADED");
        assertThat(event.details().content()).containsKey("errorMessage");
    }

    @Test
    void event_id_format_satisfies_envelope_pattern() {
        AuditEvent event = BertEvents.completed(
                "igc-bert-inference", "0.0.1", "pod-abc",
                "node-x", "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01",
                "b", 1, "v1", "x", 1.0f, 1L, 1L);
        assertThat(event.eventId()).matches("^[0-9A-HJKMNP-TV-Z]{26}$");
    }
}
