package co.uk.wolfnotsheep.enforcement.audit;

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
 * Enforcement-service helper that constructs the {@code igc-platform-audit}
 * envelope shape from the small set of fields each emit site has on hand.
 * Phase 1.12 PR5 dual-writes — every legacy
 * {@code AuditEventRepository.save(...)} call retains alongside a matching
 * {@code emitTier1/emitTier2} call here so the legacy admin UI surfaces
 * keep working while the new collector accumulates events.
 *
 * <p>Once the collector + admin UI cutover is complete (a later PR), the
 * legacy emissions can be deleted in one sweep — every call site is paired
 * with the new one.
 *
 * <p>This service emits SYSTEM-actor events only — enforcement is
 * automated, not user-driven.
 */
@Component
public class EnforcementAuditEmitter extends BaseAuditEmitter {

    public EnforcementAuditEmitter(
            ObjectProvider<AuditEmitter> emitterProvider,
            @Value("${spring.application.name:igc-governance-enforcement}") String serviceName,
            @Value("${igc.enforcement.worker.build.version:0.0.1-SNAPSHOT}") String serviceVersion,
            @Value("${HOSTNAME:unknown}") String instanceId) {
        super(emitterProvider, serviceName, serviceVersion, instanceId);
    }

    /**
     * Tier 1 (DOMAIN) emit — compliance audit, hash-chained per resource.
     * Always document-anchored in this service. SYSTEM actor.
     */
    public void emitTier1(String documentId, String eventType, String action,
                          Outcome outcome, String retentionClass,
                          Map<String, Object> metadata, Map<String, Object> content) {
        emit(documentId, eventType, Tier.DOMAIN, action, outcome,
                retentionClass == null ? "7Y" : retentionClass,
                Actor.system(serviceName, serviceVersion, instanceId),
                metadata, content);
    }

    /**
     * Tier 2 (SYSTEM) emit — operational telemetry. Failures, retries,
     * tier-decision diagnostics. Document-anchored. SYSTEM actor.
     */
    public void emitTier2(String documentId, String eventType, String action,
                          Outcome outcome, Map<String, Object> metadata, Map<String, Object> content) {
        emit(documentId, eventType, Tier.SYSTEM, action, outcome, "30D",
                Actor.system(serviceName, serviceVersion, instanceId),
                metadata, content);
    }
}
