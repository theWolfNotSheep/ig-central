package co.uk.wolfnotsheep.enforcement.audit;

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
 * Helper that constructs the {@code gls-platform-audit} envelope
 * shape from the small set of fields each enforcement-service emit
 * site has on hand. Phase 1.12 PR5 dual-writes — every legacy
 * {@code AuditEventRepository.save(...)} call retains alongside a
 * matching {@link #emit(...)} call here so the legacy admin UI
 * surfaces keep working while the new collector accumulates events.
 *
 * <p>Once the collector + admin UI cutover is complete (a later PR),
 * the legacy emissions can be deleted in one sweep — every call
 * site is paired with the new one.
 *
 * <p>The {@link AuditEmitter} bean is optional via {@link ObjectProvider} so
 * unit tests + the legacy in-process consumer path don't need to wire
 * the platform-audit auto-config. When absent, {@link #emit} is a no-op
 * (the legacy `AuditEventRepository.save` call has already fired so
 * no audit data is lost).
 */
@Component
public class EnforcementAuditEmitter {

    private static final Logger log = LoggerFactory.getLogger(EnforcementAuditEmitter.class);
    private static final String SCHEMA_VERSION = AuditEvent.CURRENT_SCHEMA_VERSION;

    /**
     * Crockford's base32 alphabet (per ULID spec — excludes I, L, O, U
     * to avoid ambiguity). The envelope schema requires
     * {@code ^[0-9A-HJKMNP-TV-Z]{26}$}.
     */
    private static final char[] CROCKFORD =
            "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final SecureRandom RNG = new SecureRandom();

    private final ObjectProvider<AuditEmitter> emitterProvider;
    private final String serviceName;
    private final String serviceVersion;
    private final String instanceId;

    public EnforcementAuditEmitter(
            ObjectProvider<AuditEmitter> emitterProvider,
            @Value("${spring.application.name:gls-governance-enforcement}") String serviceName,
            @Value("${gls.enforcement.worker.build.version:0.0.1-SNAPSHOT}") String serviceVersion,
            @Value("${HOSTNAME:unknown}") String instanceId) {
        this.emitterProvider = emitterProvider;
        this.serviceName = serviceName;
        this.serviceVersion = serviceVersion;
        this.instanceId = instanceId;
    }

    /**
     * Tier 1 (DOMAIN) emit — compliance audit, hash-chained per
     * resource. Always document-anchored in this service.
     */
    public void emitTier1(String documentId, String eventType, String action,
                          Outcome outcome, String retentionClass,
                          Map<String, Object> metadata, Map<String, Object> content) {
        emit(documentId, eventType, Tier.DOMAIN, action, outcome,
                retentionClass == null ? "7Y" : retentionClass, metadata, content);
    }

    /**
     * Tier 2 (SYSTEM) emit — operational telemetry. Failures, retries,
     * tier-decision diagnostics. Document-anchored.
     */
    public void emitTier2(String documentId, String eventType, String action,
                          Outcome outcome, Map<String, Object> metadata, Map<String, Object> content) {
        emit(documentId, eventType, Tier.SYSTEM, action, outcome,
                "30D", metadata, content);
    }

    private void emit(String documentId, String eventType, Tier tier, String action,
                      Outcome outcome, String retentionClass,
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
                    Actor.system(serviceName, serviceVersion, instanceId),
                    Resource.of(ResourceType.DOCUMENT, documentId),
                    action,
                    outcome,
                    AuditDetails.of(metadata, content),
                    retentionClass,
                    /* previousEventHash — set by Tier 1 publisher's chain head tracker
                       in a follow-up; for now the consumer-side validator handles
                       null-as-first-in-chain semantics */
                    null);
            emitter.emit(envelope);
        } catch (RuntimeException e) {
            // Never let audit emission break the enforcement flow — the legacy
            // path has already persisted the event.
            log.warn("audit emit failed for documentId={} eventType={}: {}",
                    documentId, eventType, e.getMessage());
        }
    }

    /**
     * Generates a ULID-shaped string matching the envelope schema's
     * pattern. Not strictly time-sortable like a true ULID — uses
     * SecureRandom for all 26 chars — but conforms to the Crockford
     * base32 character set so the validator accepts it. Replace with
     * a real ULID library when one is added to the platform.
     */
    private static String ulid() {
        char[] out = new char[26];
        for (int i = 0; i < out.length; i++) {
            out[i] = CROCKFORD[RNG.nextInt(CROCKFORD.length)];
        }
        return new String(out);
    }
}
