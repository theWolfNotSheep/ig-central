package co.uk.wolfnotsheep.extraction.audio.audit;

import co.uk.wolfnotsheep.platformaudit.envelope.ActorType;
import co.uk.wolfnotsheep.platformaudit.envelope.AuditEvent;
import co.uk.wolfnotsheep.platformaudit.envelope.EnvelopeValidator;
import co.uk.wolfnotsheep.platformaudit.envelope.Outcome;
import co.uk.wolfnotsheep.platformaudit.envelope.Tier;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AudioEventsTest {

    private final EnvelopeValidator validator = EnvelopeValidator.fromBundledSchema();

    @Test
    void completed_event_passes_schema_validation() {
        AuditEvent event = AudioEvents.completed(
                "gls-extraction-audio", "0.0.1", "pod-abc",
                "node-1",
                "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01",
                "audio/mpeg", "openai-whisper", "en", 12.5f, 8_192L, 4500L);

        validator.validate(event);

        assertThat(event.eventType()).isEqualTo("EXTRACTION_COMPLETED");
        assertThat(event.tier()).isEqualTo(Tier.SYSTEM);
        assertThat(event.outcome()).isEqualTo(Outcome.SUCCESS);
        assertThat(event.action()).isEqualTo("EXTRACT");
        assertThat(event.actor().type()).isEqualTo(ActorType.SYSTEM);
        assertThat(event.actor().service()).isEqualTo("gls-extraction-audio");
        assertThat(event.details().metadata()).containsEntry("provider", "openai-whisper");
        assertThat(event.details().metadata()).containsEntry("language", "en");
        assertThat(event.details().metadata()).containsEntry("durationSeconds", 12.5f);
    }

    @Test
    void completed_with_null_provider_omits_field() {
        AuditEvent event = AudioEvents.completed(
                "gls-extraction-audio", "0.0.1", "pod-abc",
                "node-2", "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01",
                null, null, null, null, 0L, 1L);

        validator.validate(event);
        assertThat(event.details().metadata()).doesNotContainKey("provider");
        assertThat(event.details().metadata()).doesNotContainKey("language");
        assertThat(event.details().metadata()).doesNotContainKey("durationSeconds");
    }

    @Test
    void failed_event_carries_AUDIO_NOT_CONFIGURED_correctly() {
        AuditEvent event = AudioEvents.failed(
                "gls-extraction-audio", "0.0.1", "pod-abc",
                "node-3", null,
                "AUDIO_NOT_CONFIGURED", "no audio transcription backend configured");

        validator.validate(event);
        assertThat(event.eventType()).isEqualTo("EXTRACTION_FAILED");
        assertThat(event.outcome()).isEqualTo(Outcome.FAILURE);
        assertThat(event.details().metadata()).containsEntry("errorCode", "AUDIO_NOT_CONFIGURED");
    }

    @Test
    void event_id_format_satisfies_envelope_pattern() {
        AuditEvent event = AudioEvents.completed(
                "gls-extraction-audio", "0.0.1", "pod-abc",
                "node-x", "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01",
                "audio/wav", "openai-whisper", "en", 1.0f, 1L, 1L);
        assertThat(event.eventId()).matches("^[0-9A-HJKMNP-TV-Z]{26}$");
    }
}
