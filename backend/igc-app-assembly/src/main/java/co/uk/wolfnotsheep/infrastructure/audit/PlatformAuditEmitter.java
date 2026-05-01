package co.uk.wolfnotsheep.infrastructure.audit;

import co.uk.wolfnotsheep.platformaudit.emit.AuditEmitter;
import co.uk.wolfnotsheep.platformaudit.emit.BaseAuditEmitter;
import co.uk.wolfnotsheep.platformaudit.envelope.Actor;
import co.uk.wolfnotsheep.platformaudit.envelope.Outcome;
import co.uk.wolfnotsheep.platformaudit.envelope.Tier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * App-assembly helper that constructs the {@code igc-platform-audit}
 * envelope shape from the small set of fields each emit site has on hand.
 * Phase 1.12 PR6 — dual-write pattern alongside legacy
 * {@code AuditEventRepository.save(...)}.
 *
 * <p>{@code igc-app-assembly} emits primarily user-driven events
 * (DOCUMENT_VIEWED, DOCUMENT_DOWNLOADED, ACCESS_DENIED,
 * CLASSIFICATION_APPROVED, CLASSIFICATION_OVERRIDDEN, DOCUMENT_FILED, etc.),
 * so the surface defaults to USER actor + DOMAIN tier via
 * {@link #emitUserAction}. {@link #emitTier1} covers system-driven Tier 1
 * events; {@link #emitTier2} covers operational telemetry.
 */
@Component
public class PlatformAuditEmitter extends BaseAuditEmitter {

    public PlatformAuditEmitter(
            ObjectProvider<AuditEmitter> emitterProvider,
            @Value("${spring.application.name:igc-app-assembly}") String serviceName,
            @Value("${igc.api.build.version:0.0.1-SNAPSHOT}") String serviceVersion,
            @Value("${HOSTNAME:unknown}") String instanceId) {
        super(emitterProvider, serviceName, serviceVersion, instanceId);
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
}
