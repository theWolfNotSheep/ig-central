package co.uk.wolfnotsheep.platformaudit.envelope;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Sanity tests for {@link EnvelopeValidator}. The bundled schema is the
 * canonical source of truth — these tests prove the wiring works against
 * the real schema, not a mock.
 */
class EnvelopeValidatorTest {

    private static final String VALID_ULID = "01HMQX9V3K5T7Z9N2B4D6F8H0J";

    private final EnvelopeValidator validator = EnvelopeValidator.fromBundledSchema();

    @Test
    void valid_system_tier_envelope_passes() {
        AuditEvent envelope = systemTierEnvelopeBuilder().build();
        validator.validate(envelope);
    }

    @Test
    void valid_domain_tier_envelope_passes() {
        AuditEvent envelope = domainTierEnvelopeBuilder().build();
        validator.validate(envelope);
    }

    @Test
    void invalid_event_id_pattern_fails() {
        AuditEvent envelope = systemTierEnvelopeBuilder()
                .eventId("not-a-ulid")
                .build();
        assertThatThrownBy(() -> validator.validate(envelope))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("eventId");
    }

    @Test
    void domain_tier_missing_resource_and_retention_class_fails() {
        AuditEvent envelope = domainTierEnvelopeBuilder()
                .resource(null)
                .retentionClass(null)
                .build();
        assertThatThrownBy(() -> validator.validate(envelope))
                .isInstanceOf(IllegalArgumentException.class)
                .satisfies(ex -> {
                    assertThat(ex.getMessage()).contains("resource");
                    assertThat(ex.getMessage()).contains("retentionClass");
                });
    }

    @Test
    void user_actor_without_id_fails() {
        AuditEvent envelope = systemTierEnvelopeBuilder()
                .actor(new Actor(ActorType.USER, "igc-app-assembly", "1.0.0", null, null))
                .build();
        assertThatThrownBy(() -> validator.validate(envelope))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("id");
    }

    @Test
    void error_message_aggregates_multiple_violations() {
        AuditEvent envelope = systemTierEnvelopeBuilder()
                .eventId("not-a-ulid")
                .schemaVersion("oops")
                .build();
        assertThatThrownBy(() -> validator.validate(envelope))
                .isInstanceOf(IllegalArgumentException.class)
                .satisfies(ex -> {
                    assertThat(ex.getMessage()).contains("eventId");
                    assertThat(ex.getMessage()).contains("schemaVersion");
                });
    }

    private EnvelopeBuilder systemTierEnvelopeBuilder() {
        return new EnvelopeBuilder()
                .eventId(VALID_ULID)
                .eventType("DOCUMENT_INGESTED")
                .tier(Tier.SYSTEM)
                .schemaVersion(AuditEvent.CURRENT_SCHEMA_VERSION)
                .timestamp(Instant.parse("2026-04-26T21:00:00Z"))
                .actor(Actor.system("igc-app-assembly", "1.0.0", "pod-abc"))
                .action("INGEST")
                .outcome(Outcome.SUCCESS)
                .details(AuditDetails.metadataOnly(Map.of("source", "test")));
    }

    private EnvelopeBuilder domainTierEnvelopeBuilder() {
        return systemTierEnvelopeBuilder()
                .tier(Tier.DOMAIN)
                .resource(Resource.of(ResourceType.DOCUMENT, "doc_123"))
                .retentionClass("7Y")
                .previousEventHash(null);
    }

    /**
     * Test-only mutable builder for {@link AuditEvent}. Production code
     * constructs records directly; we use a builder here so each test can
     * tweak one field at a time without restating the rest.
     */
    private static final class EnvelopeBuilder {
        private String eventId;
        private String eventType;
        private Tier tier;
        private String schemaVersion;
        private Instant timestamp;
        private String documentId;
        private String pipelineRunId;
        private String nodeRunId;
        private String traceparent;
        private Actor actor;
        private Resource resource;
        private String action;
        private Outcome outcome;
        private AuditDetails details;
        private String retentionClass;
        private String previousEventHash;

        EnvelopeBuilder eventId(String v) { this.eventId = v; return this; }
        EnvelopeBuilder eventType(String v) { this.eventType = v; return this; }
        EnvelopeBuilder tier(Tier v) { this.tier = v; return this; }
        EnvelopeBuilder schemaVersion(String v) { this.schemaVersion = v; return this; }
        EnvelopeBuilder timestamp(Instant v) { this.timestamp = v; return this; }
        EnvelopeBuilder actor(Actor v) { this.actor = v; return this; }
        EnvelopeBuilder resource(Resource v) { this.resource = v; return this; }
        EnvelopeBuilder action(String v) { this.action = v; return this; }
        EnvelopeBuilder outcome(Outcome v) { this.outcome = v; return this; }
        EnvelopeBuilder details(AuditDetails v) { this.details = v; return this; }
        EnvelopeBuilder retentionClass(String v) { this.retentionClass = v; return this; }
        EnvelopeBuilder previousEventHash(String v) { this.previousEventHash = v; return this; }

        AuditEvent build() {
            return new AuditEvent(
                    eventId, eventType, tier, schemaVersion, timestamp,
                    documentId, pipelineRunId, nodeRunId, traceparent,
                    actor, resource, action, outcome, details,
                    retentionClass, previousEventHash);
        }
    }
}
