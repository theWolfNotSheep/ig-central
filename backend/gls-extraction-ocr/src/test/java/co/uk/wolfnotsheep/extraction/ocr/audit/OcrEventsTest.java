package co.uk.wolfnotsheep.extraction.ocr.audit;

import co.uk.wolfnotsheep.platformaudit.envelope.ActorType;
import co.uk.wolfnotsheep.platformaudit.envelope.AuditEvent;
import co.uk.wolfnotsheep.platformaudit.envelope.EnvelopeValidator;
import co.uk.wolfnotsheep.platformaudit.envelope.Outcome;
import co.uk.wolfnotsheep.platformaudit.envelope.Tier;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OcrEventsTest {

    private final EnvelopeValidator validator = EnvelopeValidator.fromBundledSchema();

    @Test
    void completed_event_passes_schema_validation() {
        AuditEvent event = OcrEvents.completed(
                "gls-extraction-ocr", "0.0.1", "pod-abc",
                "node-1",
                "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01",
                "image/png", List.of("eng", "fra"), 1, 4_096L, 350L);

        validator.validate(event);

        assertThat(event.eventType()).isEqualTo("EXTRACTION_COMPLETED");
        assertThat(event.tier()).isEqualTo(Tier.SYSTEM);
        assertThat(event.outcome()).isEqualTo(Outcome.SUCCESS);
        assertThat(event.action()).isEqualTo("EXTRACT");
        assertThat(event.actor().type()).isEqualTo(ActorType.SYSTEM);
        assertThat(event.actor().service()).isEqualTo("gls-extraction-ocr");
        assertThat(event.details().metadata()).containsEntry("detectedMimeType", "image/png");
        assertThat(event.details().metadata()).containsEntry("languages", List.of("eng", "fra"));
        assertThat(event.details().metadata()).containsEntry("pageCount", 1);
        assertThat(event.details().metadata()).containsEntry("durationMs", 350L);
    }

    @Test
    void completed_with_null_languages_omits_field() {
        AuditEvent event = OcrEvents.completed(
                "gls-extraction-ocr", "0.0.1", "pod-abc",
                "node-2", "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01",
                null, null, null, 0L, 1L);

        validator.validate(event);
        assertThat(event.details().metadata()).doesNotContainKey("languages");
        assertThat(event.details().metadata()).doesNotContainKey("pageCount");
    }

    @Test
    void failed_event_carries_errorCode_in_metadata_and_message_in_content() {
        AuditEvent event = OcrEvents.failed(
                "gls-extraction-ocr", "0.0.1", "pod-abc",
                "node-3", null,
                "OCR_LANGUAGE_UNSUPPORTED",
                "Tesseract rejected language(s) [zzz]");

        validator.validate(event);
        assertThat(event.eventType()).isEqualTo("EXTRACTION_FAILED");
        assertThat(event.outcome()).isEqualTo(Outcome.FAILURE);
        assertThat(event.details().metadata()).containsEntry("errorCode", "OCR_LANGUAGE_UNSUPPORTED");
        assertThat(event.details().content()).containsKey("errorMessage");
    }

    @Test
    void event_id_format_satisfies_envelope_pattern() {
        AuditEvent event = OcrEvents.completed(
                "gls-extraction-ocr", "0.0.1", "pod-abc",
                "node-x", "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01",
                "image/png", List.of("eng"), null, 1L, 1L);
        assertThat(event.eventId()).matches("^[0-9A-HJKMNP-TV-Z]{26}$");
    }
}
