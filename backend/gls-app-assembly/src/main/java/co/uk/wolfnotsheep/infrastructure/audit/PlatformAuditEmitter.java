package co.uk.wolfnotsheep.infrastructure.audit;

import co.uk.wolfnotsheep.platformaudit.emit.AuditEmitter;
import co.uk.wolfnotsheep.platformaudit.envelope.Actor;
import co.uk.wolfnotsheep.platformaudit.envelope.AuditDetails;
import co.uk.wolfnotsheep.platformaudit.envelope.AuditEvent;
import co.uk.wolfnotsheep.platformaudit.envelope.Outcome;
import co.uk.wolfnotsheep.platformaudit.envelope.Resource;
import co.uk.wolfnotsheep.platformaudit.envelope.ResourceType;
import co.uk.wolfnotsheep.platformaudit.envelope.Tier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Map;

/**
 * Helper that constructs the {@code gls-platform-audit} envelope shape
 * from the small set of fields each {@code gls-app-assembly} emit site
 * has on hand. Phase 1.12 PR6 — same dual-write pattern as PR5's
 * {@code gls-governance-enforcement} migration.
 *
 * <p>{@code gls-app-assembly} emits primarily user-driven events
 * (DOCUMENT_VIEWED, DOCUMENT_DOWNLOADED, ACCESS_DENIED,
 * CLASSIFICATION_APPROVED, CLASSIFICATION_OVERRIDDEN, DOCUMENT_FILED, etc.),
 * so the surface defaults to USER actor + DOMAIN tier. {@link #emitTier2}
 * is provided for the rare operational cases.
 *
 * <p>Outline mirrors {@code EnforcementAuditEmitter} in
 * {@code gls-governance-enforcement} — when a third migration lands
 * (likely connectors), pull the shared helper into
 * {@code gls-platform-audit} to avoid copy-paste drift.
 */
@Component
public class PlatformAuditEmitter {

    private static final Logger log = LoggerFactory.getLogger(PlatformAuditEmitter.class);
    private static final String SCHEMA_VERSION = AuditEvent.CURRENT_SCHEMA_VERSION;

    /** Crockford's base32 alphabet, matching the envelope schema's ULID pattern. */
    private static final char[] CROCKFORD =
            "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final SecureRandom RNG = new SecureRandom();

    private final ObjectProvider<AuditEmitter> emitterProvider;
    private final String serviceName;
    private final String serviceVersion;
    private final String instanceId;

    public PlatformAuditEmitter(
            ObjectProvider<AuditEmitter> emitterProvider,
            @Value("${spring.application.name:gls-app-assembly}") String serviceName,
            @Value("${gls.api.build.version:0.0.1-SNAPSHOT}") String serviceVersion,
            @Value("${HOSTNAME:unknown}") String instanceId) {
        this.emitterProvider = emitterProvider;
        this.serviceName = serviceName;
        this.serviceVersion = serviceVersion;
        this.instanceId = instanceId;
    }

    /**
     * USER-actor Tier 1 emit — compliance audit for human-driven actions.
     * Default retention 7Y per the envelope schema's DOMAIN expectations.
     */
    public void emitUserAction(String documentId, String eventType, String action,
                               String userId, Outcome outcome,
                               Map<String, Object> metadata, Map<String, Object> content) {
        emit(documentId, eventType, Tier.DOMAIN, action, outcome, "7Y",
                Actor.user(userId, serviceName, serviceVersion),
                metadata, content);
    }

    /** SYSTEM-actor Tier 1 emit. */
    public void emitTier1(String documentId, String eventType, String action,
                          Outcome outcome, String retentionClass,
                          Map<String, Object> metadata, Map<String, Object> content) {
        emit(documentId, eventType, Tier.DOMAIN, action, outcome,
                retentionClass == null ? "7Y" : retentionClass,
                Actor.system(serviceName, serviceVersion, instanceId),
                metadata, content);
    }

    /** SYSTEM-actor Tier 2 emit. */
    public void emitTier2(String documentId, String eventType, String action,
                          Outcome outcome, Map<String, Object> metadata, Map<String, Object> content) {
        emit(documentId, eventType, Tier.SYSTEM, action, outcome, "30D",
                Actor.system(serviceName, serviceVersion, instanceId),
                metadata, content);
    }

    private void emit(String documentId, String eventType, Tier tier, String action,
                      Outcome outcome, String retentionClass, Actor actor,
                      Map<String, Object> metadata, Map<String, Object> content) {
        AuditEmitter emitter = emitterProvider.getIfAvailable();
        if (emitter == null) {
            // Legacy AuditEventRepository.save has already fired on the call site.
            return;
        }
        try {
            AuditEvent envelope = new AuditEvent(
                    ulid(),
                    eventType,
                    tier,
                    SCHEMA_VERSION,
                    Instant.now(),
                    documentId,
                    /* pipelineRunId */ null,
                    /* nodeRunId */ null,
                    /* traceparent */ null,
                    actor,
                    Resource.of(ResourceType.DOCUMENT, documentId),
                    action,
                    outcome,
                    AuditDetails.of(metadata, content),
                    retentionClass,
                    /* previousEventHash — set by Tier 1 publisher's chain head tracker
                       in a follow-up; collector accepts null as "first in chain" */
                    null);
            emitter.emit(envelope);
        } catch (RuntimeException e) {
            log.warn("audit emit failed for documentId={} eventType={}: {}",
                    documentId, eventType, e.getMessage());
        }
    }

    private static String ulid() {
        char[] out = new char[26];
        for (int i = 0; i < out.length; i++) {
            out[i] = CROCKFORD[RNG.nextInt(CROCKFORD.length)];
        }
        return new String(out);
    }
}
