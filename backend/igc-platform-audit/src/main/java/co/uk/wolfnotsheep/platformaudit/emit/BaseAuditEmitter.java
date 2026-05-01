package co.uk.wolfnotsheep.platformaudit.emit;

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

import java.time.Instant;
import java.util.Map;

/**
 * Shared envelope-construction logic for per-service audit emitter helpers.
 *
 * <p>Subclasses specialise by exposing convention-setting methods (default
 * actor type, default tier, default retention) and delegate to {@link #emit}
 * for the actual envelope build + persist. Keeps ULID generation, schema
 * version stamping, exception swallowing, and the absent-emitter no-op
 * pattern in one place so PR helpers don't drift.
 *
 * <p>The {@link AuditEmitter} bean is optional via {@link ObjectProvider}
 * so unit tests + legacy-only code paths don't need to wire the
 * platform-audit auto-config. When absent, {@link #emit} is a silent no-op
 * (the legacy {@code AuditEventRepository.save} call has already fired on
 * the call site so no audit data is lost during dual-write).
 *
 * <p>Subclasses construct {@link Actor} per call from {@link #serviceName},
 * {@link #serviceVersion}, {@link #instanceId} and the per-emit user id
 * (USER actor) or just the service triple (SYSTEM / CONNECTOR actor).
 */
public abstract class BaseAuditEmitter {

    private static final Logger log = LoggerFactory.getLogger(BaseAuditEmitter.class);
    private static final String SCHEMA_VERSION = AuditEvent.CURRENT_SCHEMA_VERSION;

    private final ObjectProvider<AuditEmitter> emitterProvider;
    protected final String serviceName;
    protected final String serviceVersion;
    protected final String instanceId;

    protected BaseAuditEmitter(
            ObjectProvider<AuditEmitter> emitterProvider,
            String serviceName,
            String serviceVersion,
            String instanceId) {
        this.emitterProvider = emitterProvider;
        this.serviceName = serviceName;
        this.serviceVersion = serviceVersion;
        this.instanceId = instanceId;
    }

    /**
     * Build the envelope and hand it to the underlying {@link AuditEmitter}.
     *
     * <p>Behaviour:
     * <ul>
     *   <li>If no {@link AuditEmitter} bean is registered, returns silently.</li>
     *   <li>Stamps {@code eventId} with a fresh {@link Ulid#nextId()} and
     *       {@code schemaVersion} with {@link AuditEvent#CURRENT_SCHEMA_VERSION}.</li>
     *   <li>Swallows {@link RuntimeException} from the emit path with a WARN log
     *       so audit failures cannot break the originating flow.</li>
     *   <li>Sets {@code resource} to a {@link ResourceType#DOCUMENT}-typed
     *       {@link Resource} keyed by {@code documentId}. {@code pipelineRunId},
     *       {@code nodeRunId}, {@code traceparent}, {@code previousEventHash}
     *       are passed null — request-scoped propagation lands in a follow-up.</li>
     * </ul>
     */
    protected final void emit(
            String documentId,
            String eventType,
            Tier tier,
            String action,
            Outcome outcome,
            String retentionClass,
            Actor actor,
            Map<String, Object> metadata,
            Map<String, Object> content) {
        AuditEmitter emitter = emitterProvider.getIfAvailable();
        if (emitter == null) {
            return;
        }
        try {
            AuditEvent envelope = new AuditEvent(
                    Ulid.nextId(),
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
}
