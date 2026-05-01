package co.uk.wolfnotsheep.router.web;

import co.uk.wolfnotsheep.router.api.ClassifyApi;
import co.uk.wolfnotsheep.router.audit.RouterEvents;
import co.uk.wolfnotsheep.router.jobs.JobAcquisition;
import co.uk.wolfnotsheep.router.jobs.JobStore;
import co.uk.wolfnotsheep.router.model.BlockRef;
import co.uk.wolfnotsheep.router.model.CascadeStep;
import co.uk.wolfnotsheep.router.model.ClassifyRequest;
import co.uk.wolfnotsheep.router.model.ClassifyRequestText;
import co.uk.wolfnotsheep.router.model.ClassifyRequestTextOneOf;
import co.uk.wolfnotsheep.router.model.ClassifyRequestTextOneOf1;
import co.uk.wolfnotsheep.router.model.ClassifyResponse;
import co.uk.wolfnotsheep.router.model.JobAccepted;
import co.uk.wolfnotsheep.router.parse.CascadeOutcome;
import co.uk.wolfnotsheep.router.parse.CascadeService;
import co.uk.wolfnotsheep.router.parse.RateLimitGate;
import co.uk.wolfnotsheep.platformaudit.emit.AuditEmitter;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Implements {@link ClassifyApi}. Sync (200) without
 * {@code Prefer: respond-async}, 202 with poll URL when the header is
 * set (CSV #47). Sync and async share the {@link JobStore} row for
 * idempotency — a sync request after a completed async run gets the
 * cached response; a {@code Prefer: respond-async} after a completed
 * sync run gets the COMPLETED job via the poll URL.
 */
@RestController
public class ClassifyController implements ClassifyApi {

    private static final Logger log = LoggerFactory.getLogger(ClassifyController.class);
    private static final int BYTES_PER_COST_UNIT = 1024;

    private final CascadeService cascade;
    private final JobStore jobs;
    private final ExtractMetrics metrics;
    private final ObjectMapper mapper;
    private final ObjectProvider<AuditEmitter> auditEmitterProvider;
    private final AsyncDispatcher asyncDispatcher;
    private final RateLimitGate rateLimitGate;
    private final String serviceName;
    private final String serviceVersion;
    private final String instanceId;

    public ClassifyController(
            CascadeService cascade,
            JobStore jobs,
            ExtractMetrics metrics,
            ObjectMapper mapper,
            ObjectProvider<AuditEmitter> auditEmitterProvider,
            AsyncDispatcher asyncDispatcher,
            RateLimitGate rateLimitGate,
            @Value("${spring.application.name:igc-classifier-router}") String serviceName,
            @Value("${igc.router.build.version:0.0.1-SNAPSHOT}") String serviceVersion,
            @Value("${HOSTNAME:unknown}") String instanceId) {
        this.cascade = cascade;
        this.jobs = jobs;
        this.metrics = metrics;
        // The request-side `text` is a oneOf — the cached-response
        // reads need DEDUCTION-based subtype resolution. Same Jackson
        // mixin pattern as the extraction services.
        this.mapper = mapper.copy()
                .addMixIn(ClassifyRequestText.class, ClassifyRequestTextMixin.class);
        this.auditEmitterProvider = auditEmitterProvider;
        this.asyncDispatcher = asyncDispatcher;
        this.rateLimitGate = rateLimitGate;
        this.serviceName = serviceName;
        this.serviceVersion = serviceVersion;
        this.instanceId = instanceId;
    }

    @Override
    public ResponseEntity<ClassifyResponse> classify(
            String traceparent, ClassifyRequest request, String idempotencyKey, String prefer) {

        boolean async = prefer != null && prefer.toLowerCase().contains("respond-async");
        JobAcquisition acq = jobs.tryAcquire(request.getNodeRunId());
        return switch (acq.status()) {
            case ACQUIRED -> async
                    ? handleAsyncAcquired(traceparent, request)
                    : handleSyncAcquired(traceparent, request, idempotencyKey);
            case RUNNING -> async
                    ? acceptedFor(request.getNodeRunId())
                    : runningCollision(request.getNodeRunId());
            case COMPLETED -> {
                metrics.recordIdempotencyShortCircuit("cached");
                if (async) {
                    yield acceptedFor(request.getNodeRunId());
                }
                yield ResponseEntity.ok(deserialiseCached(acq.existing().resultJson()));
            }
            case FAILED -> async
                    ? acceptedFor(request.getNodeRunId())
                    : runningCollision(request.getNodeRunId());
        };
    }

    private ResponseEntity<ClassifyResponse> runningCollision(String nodeRunId) {
        metrics.recordIdempotencyShortCircuit("in_flight");
        throw new JobInFlightException(nodeRunId);
    }

    @SuppressWarnings("unchecked")
    private ResponseEntity<ClassifyResponse> acceptedFor(String nodeRunId) {
        URI poll = URI.create("/v1/jobs/" + nodeRunId);
        JobAccepted body = new JobAccepted();
        body.setNodeRunId(nodeRunId);
        body.setStatus(JobAccepted.StatusEnum.PENDING);
        body.setPollUrl(poll);
        // The contract advertises 200 returning ClassifyResponse and
        // 202 returning JobAccepted. The generated interface narrows
        // the return type to ClassifyResponse, so the 202 body lands
        // as a raw JobAccepted via a cast — ResponseEntity.body is
        // Object at runtime; Jackson serialises JobAccepted's fields
        // cleanly.
        return (ResponseEntity<ClassifyResponse>) (ResponseEntity<?>)
                ResponseEntity.accepted().header(HttpHeaders.LOCATION, poll.toString()).body(body);
    }

    private ResponseEntity<ClassifyResponse> handleSyncAcquired(
            String traceparent, ClassifyRequest request, String idempotencyKey) {
        io.micrometer.core.instrument.Timer.Sample timer = metrics.startTimer();
        try (RateLimitGate.Token ignored = rateLimitGate.acquire()) {
            jobs.markRunning(request.getNodeRunId());
            ResponseEntity<ClassifyResponse> response = doClassify(traceparent, request, idempotencyKey);
            ClassifyResponse body = response.getBody();
            cacheCompleted(request.getNodeRunId(), body);
            recordSuccessMetrics(timer, body);
            return response;
        } catch (RuntimeException failure) {
            String code = errorCodeFor(failure);
            metrics.recordFailure(timer, code);
            jobs.markFailed(request.getNodeRunId(), code, safeMessage(failure));
            emitFailed(request, traceparent, failure);
            throw failure;
        }
    }

    private ResponseEntity<ClassifyResponse> handleAsyncAcquired(
            String traceparent, ClassifyRequest request) {
        metrics.recordIdempotencyShortCircuit("async_dispatched");
        asyncDispatcher.dispatch(request, traceparent);
        return acceptedFor(request.getNodeRunId());
    }

    /**
     * Background path. Package-private so {@link AsyncDispatcher}
     * can {@code @Async}-invoke it without bypassing Spring's AOP.
     */
    void runAsync(ClassifyRequest request, String traceparent) {
        io.micrometer.core.instrument.Timer.Sample timer = metrics.startTimer();
        try {
            jobs.markRunning(request.getNodeRunId());
            ResponseEntity<ClassifyResponse> response = doClassify(traceparent, request, null);
            cacheCompleted(request.getNodeRunId(), response.getBody());
            recordSuccessMetrics(timer, response.getBody());
        } catch (RuntimeException failure) {
            String code = errorCodeFor(failure);
            metrics.recordFailure(timer, code);
            jobs.markFailed(request.getNodeRunId(), code, safeMessage(failure));
            emitFailed(request, traceparent, failure);
        }
    }

    /**
     * Records all success-path metrics in one place so the sync and
     * async paths emit the same set: duration timer, result counter,
     * tier-by-category, cost-units, cascade-step distribution, and
     * per-step timing. Phase 2.6 PR2 wired the first three on the
     * sync path; PR-E (this PR) extends to async and adds steps +
     * per-step timing.
     */
    private void recordSuccessMetrics(io.micrometer.core.instrument.Timer.Sample timer,
                                      ClassifyResponse body) {
        String tier = body == null || body.getTierOfDecision() == null
                ? null : body.getTierOfDecision().name();
        metrics.recordSuccess(timer, tier);
        metrics.recordTierByCategory(tier, extractCategory(body));
        metrics.recordCost(tier, body == null || body.getCostUnits() == null ? 0L
                : body.getCostUnits().longValue());
        if (body != null && body.getCascadeTrace() != null) {
            metrics.recordCascadeSteps(body.getCascadeTrace().size());
            for (CascadeStep step : body.getCascadeTrace()) {
                String stepTier = step.getTier() == null ? null : step.getTier().name();
                long durationMs = step.getDurationMs() == null ? 0L : step.getDurationMs().longValue();
                boolean accepted = step.getAccepted() != null && step.getAccepted();
                metrics.recordTierStepDuration(stepTier, accepted, durationMs);
            }
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
            jobs.markCompleted(nodeRunId, json);
        } catch (JsonProcessingException e) {
            log.warn("router cache write failed for nodeRunId={}: {}", nodeRunId, e.getMessage());
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

    private static String safeMessage(Throwable t) {
        return t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
    }

    /**
     * Extract a category identifier from the cascade response for the
     * tier-by-category metric. Looks for {@code categoryCode} first
     * (low-cardinality keyword like "HR_LETTERS"), falling back to
     * {@code categoryId} (Mongo id) and then {@code category}
     * (free-text name). Returns {@code null} if none present so the
     * metrics layer can render it as {@code "unknown"}.
     */
    static String extractCategory(ClassifyResponse body) {
        if (body == null || body.getResult() == null) return null;
        Object result = body.getResult();
        if (!(result instanceof java.util.Map<?, ?> map)) return null;
        Object code = map.get("categoryCode");
        if (code instanceof String s && !s.isBlank()) return s;
        Object id = map.get("categoryId");
        if (id instanceof String s && !s.isBlank()) return s;
        Object name = map.get("category");
        if (name instanceof String s && !s.isBlank()) return s;
        return null;
    }

    private static String errorCodeFor(Throwable cause) {
        if (cause instanceof BlockNotFoundException) return "ROUTER_BLOCK_NOT_FOUND";
        if (cause instanceof JobInFlightException) return "IDEMPOTENCY_IN_FLIGHT";
        if (cause instanceof co.uk.wolfnotsheep.router.parse.RateLimitExceededException) return "ROUTER_RATE_LIMITED";
        if (cause instanceof co.uk.wolfnotsheep.router.parse.LlmJobTimeoutException) return "ROUTER_LLM_TIMEOUT";
        if (cause instanceof co.uk.wolfnotsheep.router.parse.LlmJobFailedException) return "ROUTER_LLM_FAILED";
        if (cause instanceof co.uk.wolfnotsheep.router.parse.BertBlockUnknownException) return "ROUTER_BERT_BLOCK_UNKNOWN";
        if (cause instanceof co.uk.wolfnotsheep.router.parse.SlmBlockUnknownException) return "ROUTER_SLM_BLOCK_UNKNOWN";
        if (cause instanceof co.uk.wolfnotsheep.router.parse.LlmBlockUnknownException) return "ROUTER_LLM_BLOCK_UNKNOWN";
        if (cause instanceof java.io.UncheckedIOException) return "ROUTER_DEPENDENCY_UNAVAILABLE";
        return "ROUTER_UNEXPECTED";
    }
}
