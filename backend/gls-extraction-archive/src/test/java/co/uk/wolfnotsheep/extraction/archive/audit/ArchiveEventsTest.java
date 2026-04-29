package co.uk.wolfnotsheep.extraction.archive.audit;

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
class ArchiveEventsTest {

    private final EnvelopeValidator validator = EnvelopeValidator.fromBundledSchema();

    @Test
    void completed_event_passes_schema_validation() {
        AuditEvent event = ArchiveEvents.completed(
                "gls-extraction-archive", "0.0.1", "pod-abc",
                "node-1",
                "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01",
                "zip", "application/zip",
                42, 5_120_000L, 1234L);

        validator.validate(event);

        assertThat(event.eventType()).isEqualTo("EXTRACTION_COMPLETED");
        assertThat(event.tier()).isEqualTo(Tier.SYSTEM);
        assertThat(event.outcome()).isEqualTo(Outcome.SUCCESS);
        assertThat(event.action()).isEqualTo("EXTRACT");
        assertThat(event.actor().type()).isEqualTo(ActorType.SYSTEM);
        assertThat(event.actor().service()).isEqualTo("gls-extraction-archive");
        assertThat(event.details().metadata()).containsEntry("archiveType", "zip");
        assertThat(event.details().metadata()).containsEntry("detectedMimeType", "application/zip");
        assertThat(event.details().metadata()).containsEntry("childCount", 42);
        assertThat(event.details().metadata()).containsEntry("byteCount", 5_120_000L);
        assertThat(event.details().metadata()).containsEntry("durationMs", 1234L);
    }

    @Test
    void completed_with_null_archive_type_and_mime_omits_those_fields() {
        AuditEvent event = ArchiveEvents.completed(
                "gls-extraction-archive", "0.0.1", "pod-abc",
                "node-2",
                "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01",
                null, null,
                0, 100L, 5L);

        validator.validate(event);
        assertThat(event.details().metadata()).doesNotContainKey("archiveType");
        assertThat(event.details().metadata()).doesNotContainKey("detectedMimeType");
        assertThat(event.details().metadata()).containsEntry("childCount", 0);
    }

    @Test
    void failed_event_passes_schema_validation_with_error_in_content() {
        AuditEvent event = ArchiveEvents.failed(
                "gls-extraction-archive", "0.0.1", "pod-abc",
                "node-3",
                null,
                "ARCHIVE_CORRUPT",
                "ZIP appears truncated or invalid: header is missing");

        validator.validate(event);
        assertThat(event.eventType()).isEqualTo("EXTRACTION_FAILED");
        assertThat(event.outcome()).isEqualTo(Outcome.FAILURE);
        assertThat(event.details().metadata()).containsEntry("errorCode", "ARCHIVE_CORRUPT");
        assertThat(event.details().content()).containsKey("errorMessage");
    }

    @Test
    void event_id_format_satisfies_envelope_pattern() {
        AuditEvent event = ArchiveEvents.completed(
                "gls-extraction-archive", "0.0.1", "pod-abc",
                "node-x",
                "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01",
                "mbox", "application/mbox",
                3, 11L, 1L);
        assertThat(event.eventId()).matches("^[0-9A-HJKMNP-TV-Z]{26}$");
    }
}
