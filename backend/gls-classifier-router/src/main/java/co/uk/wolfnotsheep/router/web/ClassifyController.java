package co.uk.wolfnotsheep.router.web;

import co.uk.wolfnotsheep.router.api.ClassifyApi;
import co.uk.wolfnotsheep.router.audit.RouterEvents;
import co.uk.wolfnotsheep.router.idempotency.IdempotencyInFlightException;
import co.uk.wolfnotsheep.router.idempotency.IdempotencyOutcome;
import co.uk.wolfnotsheep.router.idempotency.IdempotencyStore;
import co.uk.wolfnotsheep.router.model.BlockRef;
import co.uk.wolfnotsheep.router.model.CascadeStep;
import co.uk.wolfnotsheep.router.model.ClassifyRequest;
import co.uk.wolfnotsheep.router.model.ClassifyRequestText;
import co.uk.wolfnotsheep.router.model.ClassifyRequestTextOneOf;
import co.uk.wolfnotsheep.router.model.ClassifyRequestTextOneOf1;
import co.uk.wolfnotsheep.router.model.ClassifyResponse;
import co.uk.wolfnotsheep.router.parse.CascadeOutcome;
import co.uk.wolfnotsheep.router.parse.CascadeService;
import co.uk.wolfnotsheep.platformaudit.emit.AuditEmitter;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Implements {@link ClassifyApi}. Phase 1.2 first cut wires the
 * {@link CascadeService} (currently the deterministic mock); real
 * tiers fill in behind the same interface across 1.4–1.6.
 */
@RestController
public class ClassifyController implements ClassifyApi {

    private static final Logger log = LoggerFactory.getLogger(ClassifyController.class);
    private static final int BYTES_PER_COST_UNIT = 1024;

    private final CascadeService cascade;
    private final IdempotencyStore idempotency;
    private final ExtractMetrics metrics;
    private final ObjectMapper mapper;
    private final ObjectProvider<AuditEmitter> auditEmitterProvider;
    private final String serviceName;
    private final String serviceVersion;
    private final String instanceId;

    public ClassifyController(
            CascadeService cascade,
            IdempotencyStore idempotency,
            ExtractMetrics metrics,
            ObjectMapper mapper,
            ObjectProvider<AuditEmitter> auditEmitterProvider,
            @Value("${spring.application.name:gls-classifier-router}") String serviceName,
            @Value("${gls.router.build.version:0.0.1-SNAPSHOT}") String serviceVersion,
            @Value("${HOSTNAME:unknown}") String instanceId) {
        this.cascade = cascade;
        this.idempotency = idempotency;
        this.metrics = metrics;
        // The request-side `text` is a oneOf — the cached-response
        // reads need DEDUCTION-based subtype resolution. Same Jackson
        // mixin pattern as the extraction services.
        this.mapper = mapper.copy()
                .addMixIn(ClassifyRequestText.class, ClassifyRequestTextMixin.class);
        this.auditEmitterProvider = auditEmitterProvider;
        this.serviceName = serviceName;
        this.serviceVersion = serviceVersion;
        this.instanceId = instanceId;
    }

    @Override
    public ResponseEntity<ClassifyResponse> classify(
            String traceparent, ClassifyRequest request, String idempotencyKey) {

        IdempotencyOutcome outcome = idempotency.tryAcquire(request.getNodeRunId());
        switch (outcome.status()) {
            case CACHED -> {
                metrics.recordIdempotencyShortCircuit("cached");
                return ResponseEntity.ok(deserialiseCached(outcome.cachedJson()));
            }
            case IN_FLIGHT -> {
                metrics.recordIdempotencyShortCircuit("in_flight");
                throw new IdempotencyInFlightException(request.getNodeRunId());
            }
            case ACQUIRED -> { /* fall through */ }
        }

        io.micrometer.core.instrument.Timer.Sample timer = metrics.startTimer();
        try {
            ResponseEntity<ClassifyResponse> response = doClassify(traceparent, request, idempotencyKey);
            ClassifyResponse body = response.getBody();
            cacheCompleted(request.getNodeRunId(), body);
            metrics.recordSuccess(timer, body == null || body.getTierOfDecision() == null
                    ? null : body.getTierOfDecision().name());
            return response;
        } catch (RuntimeException failure) {
            metrics.recordFailure(timer, errorCodeFor(failure));
            idempotency.releaseOnFailure(request.getNodeRunId());
            emitFailed(request, traceparent, failure);
            throw failure;
        }
    }

    private ResponseEntity<ClassifyResponse> doClassify(
            String traceparent, ClassifyRequest request, String idempotencyKey) {

        Instant started = Instant.now();
        if (idempotencyKey != null) {
            log.debug("classify: nodeRunId={} idempotencyKey={}",
                    request.getNodeRunId(), idempotencyKey);
        }

        BlockRef block = request.getBlock();
        String blockId = block == null ? null : block.getId();
        Integer blockVersion = block == null ? null : block.getVersion();
        String blockType = block == null || block.getType() == null
                ? null : block.getType().name();
        String text = inlineText(request.getText());

        CascadeOutcome outcome = cascade.run(blockId, blockVersion, blockType, text);

        long durationMs = Duration.between(started, Instant.now()).toMillis();
        emitCompleted(request, traceparent, outcome, durationMs);

        ClassifyResponse response = buildResponse(request, outcome, durationMs);
        return ResponseEntity.ok(response);
    }

    private static ClassifyResponse buildResponse(
            ClassifyRequest request, CascadeOutcome outcome, long durationMs) {
        ClassifyResponse response = new ClassifyResponse();
        response.setNodeRunId(request.getNodeRunId());
        response.setBlock(request.getBlock());
        response.setTierOfDecision(toApi(outcome.tierOfDecision()));
        response.setConfidence(outcome.confidence());
        response.setResult(outcome.result());
        if (outcome.rationale() != null) response.setRationale(outcome.rationale());
        if (outcome.evidence() != null && !outcome.evidence().isEmpty()) {
            response.setEvidence(outcome.evidence());
        }
        if (outcome.trace() != null && !outcome.trace().isEmpty()) {
            List<CascadeStep> steps = new ArrayList<>(outcome.trace().size());
            for (CascadeOutcome.TraceStep s : outcome.trace()) {
                steps.add(toApiStep(s));
            }
            response.setCascadeTrace(steps);
        }
        response.setDurationMs((int) Math.min(Integer.MAX_VALUE, durationMs));
        long costUnits = Math.max(0L, outcome.byteCount() / BYTES_PER_COST_UNIT) + outcome.costUnits();
        response.setCostUnits((int) Math.min(Integer.MAX_VALUE, costUnits));
        return response;
    }

    private static CascadeStep toApiStep(CascadeOutcome.TraceStep s) {
        CascadeStep step = new CascadeStep();
        step.setTier(CascadeStep.TierEnum.fromValue(s.tier()));
        step.setAccepted(s.accepted());
        if (s.confidence() != null) step.setConfidence(s.confidence());
        if (s.durationMs() != null) step.setDurationMs(s.durationMs().intValue());
        if (s.costUnits() != null) step.setCostUnits(s.costUnits().intValue());
        if (s.errorCode() != null) step.setErrorCode(s.errorCode());
        return step;
    }

    private static ClassifyResponse.TierOfDecisionEnum toApi(String tier) {
        return switch (tier) {
            case "BERT" -> ClassifyResponse.TierOfDecisionEnum.BERT;
            case "SLM" -> ClassifyResponse.TierOfDecisionEnum.SLM;
            case "LLM" -> ClassifyResponse.TierOfDecisionEnum.LLM;
            case "ROUTER_SHORT_CIRCUIT" -> ClassifyResponse.TierOfDecisionEnum.ROUTER_SHORT_CIRCUIT;
            default -> ClassifyResponse.TierOfDecisionEnum.MOCK;
        };
    }

    private static String inlineText(ClassifyRequestText text) {
        if (text instanceof ClassifyRequestTextOneOf inline) {
            return inline.getText() == null ? "" : inline.getText();
        }
        // textRef branch — Phase 1.2 first cut doesn't fetch; the
        // mock returns the same shape regardless. The follow-up
        // wires a small MinIO fetcher behind this.
        return "";
    }

    private ClassifyResponse deserialiseCached(String json) {
        try {
            return mapper.readValue(json, ClassifyResponse.class);
        } catch (JsonProcessingException e) {
            log.warn("idempotency cache deserialise failed: {}", e.getMessage());
            throw new IllegalStateException("idempotency cache row was unparseable", e);
        }
    }

    private void cacheCompleted(String nodeRunId, ClassifyResponse response) {
        if (response == null) return;
        try {
            String json = mapper.writeValueAsString(response);
            idempotency.cacheResult(nodeRunId, json);
        } catch (JsonProcessingException e) {
            log.warn("idempotency cache write failed for nodeRunId={}: {}",
                    nodeRunId, e.getMessage());
        }
    }

    private void emitCompleted(ClassifyRequest request, String traceparent,
                               CascadeOutcome outcome, long durationMs) {
        AuditEmitter emitter = auditEmitterProvider.getIfAvailable();
        if (emitter == null) return;
        BlockRef block = request.getBlock();
        try {
            emitter.emit(RouterEvents.completed(
                    serviceName, serviceVersion, instanceId,
                    request.getNodeRunId(), traceparent,
                    block == null ? null : block.getId(),
                    block == null ? null : block.getVersion(),
                    outcome.tierOfDecision(), outcome.confidence(),
                    outcome.byteCount(), durationMs));
        } catch (RuntimeException e) {
            log.warn("audit emit (CLASSIFY_COMPLETED) failed for nodeRunId={}: {}",
                    request.getNodeRunId(), e.getMessage());
        }
    }

    private void emitFailed(ClassifyRequest request, String traceparent, Throwable cause) {
        AuditEmitter emitter = auditEmitterProvider.getIfAvailable();
        if (emitter == null) return;
        String errorCode = errorCodeFor(cause);
        String nodeRunId = request != null ? request.getNodeRunId() : null;
        try {
            emitter.emit(RouterEvents.failed(
                    serviceName, serviceVersion, instanceId,
                    nodeRunId, traceparent, errorCode, cause.getMessage()));
        } catch (RuntimeException e) {
            log.warn("audit emit (CLASSIFY_FAILED) failed for nodeRunId={}: {}",
                    nodeRunId, e.getMessage());
        }
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.DEDUCTION)
    @JsonSubTypes({
            @JsonSubTypes.Type(ClassifyRequestTextOneOf.class),
            @JsonSubTypes.Type(ClassifyRequestTextOneOf1.class)
    })
    abstract static class ClassifyRequestTextMixin {
    }

    private static String errorCodeFor(Throwable cause) {
        if (cause instanceof BlockNotFoundException) return "ROUTER_BLOCK_NOT_FOUND";
        if (cause instanceof IdempotencyInFlightException) return "IDEMPOTENCY_IN_FLIGHT";
        if (cause instanceof co.uk.wolfnotsheep.router.parse.LlmJobTimeoutException) return "ROUTER_LLM_TIMEOUT";
        if (cause instanceof co.uk.wolfnotsheep.router.parse.LlmJobFailedException) return "ROUTER_LLM_FAILED";
        if (cause instanceof co.uk.wolfnotsheep.router.parse.BertBlockUnknownException) return "ROUTER_BERT_BLOCK_UNKNOWN";
        if (cause instanceof java.io.UncheckedIOException) return "ROUTER_DEPENDENCY_UNAVAILABLE";
        return "ROUTER_UNEXPECTED";
    }
}
