package co.uk.wolfnotsheep.enforcement.audit;

import co.uk.wolfnotsheep.platformaudit.emit.AuditEmitter;
import co.uk.wolfnotsheep.platformaudit.envelope.ActorType;
import co.uk.wolfnotsheep.platformaudit.envelope.AuditEvent;
import co.uk.wolfnotsheep.platformaudit.envelope.Outcome;
import co.uk.wolfnotsheep.platformaudit.envelope.ResourceType;
import co.uk.wolfnotsheep.platformaudit.envelope.Tier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EnforcementAuditEmitterTest {

    private AuditEmitter emitter;
    private EnforcementAuditEmitter helper;

    @BeforeEach
    void setUp() {
        emitter = mock(AuditEmitter.class);
        helper = new EnforcementAuditEmitter(
                providerOf(emitter), "igc-governance-enforcement", "1.2.3", "test-pod");
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<AuditEmitter> providerOf(AuditEmitter e) {
        ObjectProvider<AuditEmitter> p = mock(ObjectProvider.class);
        when(p.getIfAvailable()).thenReturn(e);
        return p;
    }

    @Test
    void emitTier1_constructs_envelope_with_DOMAIN_tier_and_document_resource() {
        helper.emitTier1("doc-1", "GOVERNANCE_APPLIED", "ENFORCE_GOVERNANCE",
                Outcome.SUCCESS, "7Y",
                Map.of("category", "HR > Letters"),
                Map.of("rationale", "matched HR taxonomy"));

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(emitter, times(1)).emit(captor.capture());
        AuditEvent env = captor.getValue();

        assertThat(env.eventId()).matches("^[0-9A-HJKMNP-TV-Z]{26}$");
        assertThat(env.eventType()).isEqualTo("GOVERNANCE_APPLIED");
        assertThat(env.tier()).isEqualTo(Tier.DOMAIN);
        assertThat(env.schemaVersion()).isEqualTo(AuditEvent.CURRENT_SCHEMA_VERSION);
        assertThat(env.documentId()).isEqualTo("doc-1");
        assertThat(env.action()).isEqualTo("ENFORCE_GOVERNANCE");
        assertThat(env.outcome()).isEqualTo(Outcome.SUCCESS);
        assertThat(env.retentionClass()).isEqualTo("7Y");

        assertThat(env.actor().type()).isEqualTo(ActorType.SYSTEM);
        assertThat(env.actor().service()).isEqualTo("igc-governance-enforcement");
        assertThat(env.actor().version()).isEqualTo("1.2.3");
        assertThat(env.actor().instance()).isEqualTo("test-pod");

        assertThat(env.resource().type()).isEqualTo(ResourceType.DOCUMENT);
        assertThat(env.resource().id()).isEqualTo("doc-1");

        assertThat(env.details().metadata()).containsEntry("category", "HR > Letters");
        assertThat(env.details().content()).containsEntry("rationale", "matched HR taxonomy");

        assertThat(env.previousEventHash()).isNull();
    }

    @Test
    void emitTier2_constructs_envelope_with_SYSTEM_tier_and_30D_default() {
        helper.emitTier2("doc-2", "STORAGE_MIGRATION_FAILED", "MIGRATE_TIER",
                Outcome.FAILURE,
                Map.of("targetTierId", "tier-cold"),
                Map.of("error", "MinIO unreachable"));

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(emitter, times(1)).emit(captor.capture());
        AuditEvent env = captor.getValue();

        assertThat(env.tier()).isEqualTo(Tier.SYSTEM);
        assertThat(env.outcome()).isEqualTo(Outcome.FAILURE);
        assertThat(env.retentionClass()).isEqualTo("30D");
    }

    @Test
    void emit_swallows_exceptions_so_audit_failures_dont_break_enforcement() {
        org.mockito.Mockito.doThrow(new RuntimeException("rabbit down"))
                .when(emitter).emit(org.mockito.ArgumentMatchers.any());

        assertThatNoException().isThrownBy(() -> helper.emitTier1(
                "doc-3", "GOVERNANCE_APPLIED", "ENFORCE_GOVERNANCE",
                Outcome.SUCCESS, "7Y", Map.of(), null));
    }

    @Test
    void absent_emitter_bean_is_silent_no_op() {
        EnforcementAuditEmitter helperNoEmitter = new EnforcementAuditEmitter(
                providerOf(null), "igc-governance-enforcement", "1.2.3", "test-pod");

        assertThatNoException().isThrownBy(() -> helperNoEmitter.emitTier1(
                "doc-4", "GOVERNANCE_APPLIED", "ENFORCE_GOVERNANCE",
                Outcome.SUCCESS, "7Y", Map.of(), null));

        verify(emitter, never()).emit(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void emitTier1_defaults_retention_to_7Y_when_null_passed() {
        helper.emitTier1("doc-5", "DOCUMENT_DISPOSED", "DELETE",
                Outcome.SUCCESS, /* retentionClass */ null,
                Map.of("trigger", "RETENTION_EXPIRED"), null);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(emitter, times(1)).emit(captor.capture());
        assertThat(captor.getValue().retentionClass()).isEqualTo("7Y");
    }
}
