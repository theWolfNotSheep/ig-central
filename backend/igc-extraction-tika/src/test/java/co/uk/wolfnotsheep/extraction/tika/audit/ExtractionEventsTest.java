package co.uk.wolfnotsheep.extraction.tika.audit;

import co.uk.wolfnotsheep.platformaudit.envelope.ActorType;
import co.uk.wolfnotsheep.platformaudit.envelope.AuditEvent;
import co.uk.wolfnotsheep.platformaudit.envelope.EnvelopeValidator;
import co.uk.wolfnotsheep.platformaudit.envelope.Outcome;
import co.uk.wolfnotsheep.platformaudit.envelope.Tier;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke tests for the audit-event factory. Each test runs the constructed
 * envelope through {@link EnvelopeValidator#fromBundledSchema()} so we
 * verify against the canonical schema, not just our local invariants.
 */
class ExtractionEventsTest {

    private final EnvelopeValidator validator = EnvelopeValidator.fromBundledSchema();

    @Test
    void completed_event_passes_schema_validation() {
        AuditEvent event = ExtractionEvents.completed(
                "igc-extraction-tika", "0.0.1", "pod-abc",
                "node-1",
                "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01",
                "application/pdf", 12, 102_400L, 234L, false);

        validator.validate(event);

        assertThat(event.eventType()).isEqualTo("EXTRACTION_COMPLETED");
        assertThat(event.tier()).isEqualTo(Tier.SYSTEM);
        assertThat(event.outcome()).isEqualTo(Outcome.SUCCESS);
        assertThat(event.action()).isEqualTo("EXTRACT");
        assertThat(event.actor().type()).isEqualTo(ActorType.SYSTEM);
        assertThat(event.actor().service()).isEqualTo("igc-extraction-tika");
        assertThat(event.details().metadata()).containsEntry("detectedMimeType", "application/pdf");
        assertThat(event.details().metadata()).containsEntry("pageCount", 12);
        assertThat(event.details().metadata()).containsEntry("byteCount", 102_400L);
        assertThat(event.details().metadata()).containsEntry("durationMs", 234L);
        assertThat(event.details().metadata()).containsEntry("truncated", false);
    }

    @Test
    void completed_with_null_mime_and_page_omits_those_fields() {
        AuditEvent event = ExtractionEvents.completed(
                "igc-extraction-tika", "0.0.1", "pod-abc",
                "node-2",
                "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01",
                null, null, 100L, 5L, true);

        validator.validate(event);
        assertThat(event.details().metadata()).doesNotContainKey("detectedMimeType");
        assertThat(event.details().metadata()).doesNotContainKey("pageCount");
        assertThat(event.details().metadata()).containsEntry("truncated", true);
    }

    @Test
    void failed_event_passes_schema_validation_with_error_in_content() {
        AuditEvent event = ExtractionEvents.failed(
                "igc-extraction-tika", "0.0.1", "pod-abc",
                "node-3",
                null,
                "EXTRACTION_CORRUPT",
                "Tika could not parse the document: bad header");

        validator.validate(event);
        assertThat(event.eventType()).isEqualTo("EXTRACTION_FAILED");
        assertThat(event.outcome()).isEqualTo(Outcome.FAILURE);
        assertThat(event.details().metadata()).containsEntry("errorCode", "EXTRACTION_CORRUPT");
        assertThat(event.details().content()).containsKey("errorMessage");
    }

    @Test
    void event_id_format_satisfies_envelope_pattern() {
        AuditEvent event = ExtractionEvents.completed(
                "igc-extraction-tika", "0.0.1", "pod-abc",
                "node-x",
                "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01",
                "text/plain", null, 11L, 1L, false);
        // The schema validator covers the regex; this is a belt-and-braces
        // assertion making the format requirement obvious in the test
        // surface (Crockford base32, 26 chars, excluding I L O U).
        assertThat(event.eventId()).matches("^[0-9A-HJKMNP-TV-Z]{26}$");
    }
}
