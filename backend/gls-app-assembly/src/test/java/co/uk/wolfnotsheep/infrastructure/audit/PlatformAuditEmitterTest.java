package co.uk.wolfnotsheep.infrastructure.audit;

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

class PlatformAuditEmitterTest {

    private AuditEmitter emitter;
    private PlatformAuditEmitter helper;

    @BeforeEach
    void setUp() {
        emitter = mock(AuditEmitter.class);
        helper = new PlatformAuditEmitter(
                providerOf(emitter), "gls-app-assembly", "1.2.3", "test-pod");
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<AuditEmitter> providerOf(AuditEmitter e) {
        ObjectProvider<AuditEmitter> p = mock(ObjectProvider.class);
        when(p.getIfAvailable()).thenReturn(e);
        return p;
    }

    @Test
    void emitUserAction_constructs_envelope_with_USER_actor_and_DOMAIN_tier() {
        helper.emitUserAction("doc-1", "DOCUMENT_VIEWED", "VIEW", "alice@example.com",
                Outcome.SUCCESS,
                Map.of("category", "HR > Letters"),
                Map.of("fileName", "letter.pdf"));

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(emitter, times(1)).emit(captor.capture());
        AuditEvent env = captor.getValue();

        assertThat(env.eventId()).matches("^[0-9A-HJKMNP-TV-Z]{26}$");
        assertThat(env.eventType()).isEqualTo("DOCUMENT_VIEWED");
        assertThat(env.tier()).isEqualTo(Tier.DOMAIN);
        assertThat(env.documentId()).isEqualTo("doc-1");
        assertThat(env.action()).isEqualTo("VIEW");
        assertThat(env.outcome()).isEqualTo(Outcome.SUCCESS);
        assertThat(env.retentionClass()).isEqualTo("7Y");

        assertThat(env.actor().type()).isEqualTo(ActorType.USER);
        assertThat(env.actor().id()).isEqualTo("alice@example.com");
        assertThat(env.actor().service()).isEqualTo("gls-app-assembly");
        assertThat(env.actor().version()).isEqualTo("1.2.3");
        // USER actor has no instance per Actor.user() factory
        assertThat(env.actor().instance()).isNull();

        assertThat(env.resource().type()).isEqualTo(ResourceType.DOCUMENT);
        assertThat(env.resource().id()).isEqualTo("doc-1");

        assertThat(env.details().metadata()).containsEntry("category", "HR > Letters");
        assertThat(env.details().content()).containsEntry("fileName", "letter.pdf");
    }

    @Test
    void emitTier1_constructs_envelope_with_SYSTEM_actor() {
        helper.emitTier1("doc-2", "GOVERNANCE_APPLIED", "ENFORCE_GOVERNANCE",
                Outcome.SUCCESS, "7Y",
                Map.of("category", "HR"), Map.of("rationale", "matched"));

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(emitter, times(1)).emit(captor.capture());
        AuditEvent env = captor.getValue();

        assertThat(env.actor().type()).isEqualTo(ActorType.SYSTEM);
        assertThat(env.actor().instance()).isEqualTo("test-pod");
        assertThat(env.tier()).isEqualTo(Tier.DOMAIN);
    }

    @Test
    void emitTier2_constructs_envelope_with_SYSTEM_tier_and_30D_default() {
        helper.emitTier2("doc-3", "VIEW_LATENCY_HIGH", "VIEW",
                Outcome.PARTIAL,
                Map.of("p99Ms", 1200), Map.of("threshold", "1000ms"));

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(emitter, times(1)).emit(captor.capture());
        AuditEvent env = captor.getValue();

        assertThat(env.tier()).isEqualTo(Tier.SYSTEM);
        assertThat(env.outcome()).isEqualTo(Outcome.PARTIAL);
        assertThat(env.retentionClass()).isEqualTo("30D");
    }

    @Test
    void emit_swallows_exceptions_so_audit_failures_dont_break_callers() {
        org.mockito.Mockito.doThrow(new RuntimeException("rabbit down"))
                .when(emitter).emit(org.mockito.ArgumentMatchers.any());

        assertThatNoException().isThrownBy(() -> helper.emitUserAction(
                "doc-x", "DOCUMENT_VIEWED", "VIEW", "alice@example.com",
                Outcome.SUCCESS, Map.of(), null));
    }

    @Test
    void absent_emitter_bean_is_silent_no_op() {
        PlatformAuditEmitter noEmitter = new PlatformAuditEmitter(
                providerOf(null), "gls-app-assembly", "1.2.3", "test-pod");

        assertThatNoException().isThrownBy(() -> noEmitter.emitUserAction(
                "doc-y", "DOCUMENT_VIEWED", "VIEW", "alice@example.com",
                Outcome.SUCCESS, Map.of(), null));

        verify(emitter, never()).emit(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void emitTier1_defaults_retention_to_7Y_when_null_passed() {
        helper.emitTier1("doc-5", "GOVERNANCE_APPLIED", "ENFORCE_GOVERNANCE",
                Outcome.SUCCESS, /* retentionClass */ null,
                Map.of(), null);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(emitter, times(1)).emit(captor.capture());
        assertThat(captor.getValue().retentionClass()).isEqualTo("7Y");
    }
}
