package co.uk.wolfnotsheep.auditcollector.consumer;

import co.uk.wolfnotsheep.auditcollector.store.StoredTier1Event;
import co.uk.wolfnotsheep.auditcollector.store.StoredTier2Event;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EnvelopeMapperTest {

    @Test
    void tier1_envelope_denormalises_all_known_fields() {
        Map<String, Object> envelope = Map.ofEntries(
                Map.entry("eventId", "01HMQX9V3K5T7Z9N2B4D6F8H0J"),
                Map.entry("eventType", "DOCUMENT_CLASSIFIED"),
                Map.entry("tier", "DOMAIN"),
                Map.entry("schemaVersion", "1.0.0"),
                Map.entry("timestamp", "2026-04-30T10:00:00Z"),
                Map.entry("documentId", "doc-1"),
                Map.entry("actor", Map.of("type", "SYSTEM", "service", "igc-classifier-router")),
                Map.entry("resource", Map.of("type", "DOCUMENT", "id", "doc-1")),
                Map.entry("action", "CLASSIFY"),
                Map.entry("outcome", "SUCCESS"),
                Map.entry("retentionClass", "7Y")
        );

        StoredTier1Event row = EnvelopeMapper.toTier1(envelope);

        assertThat(row.getEventId()).isEqualTo("01HMQX9V3K5T7Z9N2B4D6F8H0J");
        assertThat(row.getEventType()).isEqualTo("DOCUMENT_CLASSIFIED");
        assertThat(row.getTier()).isEqualTo("DOMAIN");
        assertThat(row.getSchemaVersion()).isEqualTo("1.0.0");
        assertThat(row.getTimestamp()).isEqualTo(Instant.parse("2026-04-30T10:00:00Z"));
        assertThat(row.getDocumentId()).isEqualTo("doc-1");
        assertThat(row.getActorService()).isEqualTo("igc-classifier-router");
        assertThat(row.getActorType()).isEqualTo("SYSTEM");
        assertThat(row.getResourceType()).isEqualTo("DOCUMENT");
        assertThat(row.getResourceId()).isEqualTo("doc-1");
        assertThat(row.getAction()).isEqualTo("CLASSIFY");
        assertThat(row.getOutcome()).isEqualTo("SUCCESS");
        assertThat(row.getRetentionClass()).isEqualTo("7Y");
        assertThat(row.getEnvelope()).containsKey("actor");
    }

    @Test
    void tier1_envelope_missing_optional_actor_yields_null_actorService() {
        Map<String, Object> envelope = Map.of(
                "eventId", "01HMQX9V3K5T7Z9N2B4D6F8H0J",
                "eventType", "DOCUMENT_CLASSIFIED",
                "schemaVersion", "1.0.0",
                "timestamp", "2026-04-30T10:00:00Z"
        );

        StoredTier1Event row = EnvelopeMapper.toTier1(envelope);

        assertThat(row.getActorService()).isNull();
        assertThat(row.getResourceType()).isNull();
    }

    @Test
    void tier2_envelope_marks_tier_SYSTEM() {
        Map<String, Object> envelope = Map.of(
                "eventId", "01HMQX9V3K5T7Z9N2B4D6F8H0K",
                "eventType", "MCP_TOOL_CALLED",
                "schemaVersion", "1.0.0",
                "timestamp", "2026-04-30T10:00:00Z"
        );

        StoredTier2Event row = EnvelopeMapper.toTier2(envelope);

        assertThat(row.getTier()).isEqualTo("SYSTEM");
        assertThat(row.getEventType()).isEqualTo("MCP_TOOL_CALLED");
    }

    @Test
    void invalid_timestamp_yields_null() {
        Map<String, Object> envelope = Map.of(
                "eventId", "01HMQX9V3K5T7Z9N2B4D6F8H0J",
                "eventType", "DOCUMENT_CLASSIFIED",
                "schemaVersion", "1.0.0",
                "timestamp", "not-a-real-instant"
        );

        StoredTier1Event row = EnvelopeMapper.toTier1(envelope);

        assertThat(row.getTimestamp()).isNull();
    }
}
