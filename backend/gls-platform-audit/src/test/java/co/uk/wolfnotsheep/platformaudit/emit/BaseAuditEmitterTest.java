package co.uk.wolfnotsheep.platformaudit.emit;

import co.uk.wolfnotsheep.platformaudit.envelope.Actor;
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

class BaseAuditEmitterTest {

    /** Minimal subclass that exposes {@link BaseAuditEmitter#emit} for direct test. */
    private static class TestEmitter extends BaseAuditEmitter {
        TestEmitter(ObjectProvider<AuditEmitter> emitterProvider, String s, String v, String i) {
            super(emitterProvider, s, v, i);
        }
        void doEmit(String documentId, String eventType, Tier tier, String action,
                    Outcome outcome, String retentionClass, Actor actor,
                    Map<String, Object> metadata, Map<String, Object> content) {
            emit(documentId, eventType, tier, action, outcome, retentionClass,
                    actor, metadata, content);
        }
    }

    private AuditEmitter underlying;
    private TestEmitter helper;

    @BeforeEach
    void setUp() {
        underlying = mock(AuditEmitter.class);
        helper = new TestEmitter(providerOf(underlying), "test-svc", "1.0.0", "pod-x");
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<AuditEmitter> providerOf(AuditEmitter e) {
        ObjectProvider<AuditEmitter> p = mock(ObjectProvider.class);
        when(p.getIfAvailable()).thenReturn(e);
        return p;
    }

    @Test
    void emit_builds_envelope_with_ulid_eventId_current_schema_and_document_resource() {
        helper.doEmit("doc-1", "DOCUMENT_VIEWED", Tier.DOMAIN, "VIEW",
                Outcome.SUCCESS, "7Y",
                Actor.user("alice@example.com", "test-svc", "1.0.0"),
                Map.of("k", "v"), Map.of("body", "x"));

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(underlying, times(1)).emit(captor.capture());
        AuditEvent env = captor.getValue();

        assertThat(env.eventId()).matches("^[0-9A-HJKMNP-TV-Z]{26}$");
        assertThat(env.schemaVersion()).isEqualTo(AuditEvent.CURRENT_SCHEMA_VERSION);
        assertThat(env.tier()).isEqualTo(Tier.DOMAIN);
        assertThat(env.eventType()).isEqualTo("DOCUMENT_VIEWED");
        assertThat(env.documentId()).isEqualTo("doc-1");
        assertThat(env.resource().type()).isEqualTo(ResourceType.DOCUMENT);
        assertThat(env.resource().id()).isEqualTo("doc-1");
        assertThat(env.action()).isEqualTo("VIEW");
        assertThat(env.outcome()).isEqualTo(Outcome.SUCCESS);
        assertThat(env.retentionClass()).isEqualTo("7Y");
        assertThat(env.pipelineRunId()).isNull();
        assertThat(env.nodeRunId()).isNull();
        assertThat(env.traceparent()).isNull();
        assertThat(env.previousEventHash()).isNull();
        assertThat(env.details().metadata()).containsEntry("k", "v");
        assertThat(env.details().content()).containsEntry("body", "x");
    }

    @Test
    void emit_swallows_runtime_exceptions_from_underlying_emitter() {
        org.mockito.Mockito.doThrow(new RuntimeException("rabbit down"))
                .when(underlying).emit(org.mockito.ArgumentMatchers.any());

        assertThatNoException().isThrownBy(() -> helper.doEmit(
                "doc-x", "DOCUMENT_VIEWED", Tier.DOMAIN, "VIEW", Outcome.SUCCESS, "7Y",
                Actor.system("test-svc", "1.0.0", "pod-x"), Map.of(), null));
    }

    @Test
    void absent_emitter_bean_is_silent_no_op() {
        TestEmitter noEmitter = new TestEmitter(providerOf(null), "test-svc", "1.0.0", "pod-x");

        assertThatNoException().isThrownBy(() -> noEmitter.doEmit(
                "doc-y", "DOCUMENT_VIEWED", Tier.DOMAIN, "VIEW", Outcome.SUCCESS, "7Y",
                Actor.system("test-svc", "1.0.0", "pod-x"), Map.of(), null));

        verify(underlying, never()).emit(org.mockito.ArgumentMatchers.any());
    }
}
