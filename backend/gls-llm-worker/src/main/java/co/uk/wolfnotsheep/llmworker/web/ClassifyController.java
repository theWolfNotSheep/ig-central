package co.uk.wolfnotsheep.llmworker.web;

import co.uk.wolfnotsheep.llmworker.api.ClassifyApi;
import co.uk.wolfnotsheep.llmworker.audit.LlmEvents;
import co.uk.wolfnotsheep.llmworker.backend.BlockUnknownException;
import co.uk.wolfnotsheep.llmworker.backend.LlmNotConfiguredException;
import co.uk.wolfnotsheep.llmworker.backend.LlmResult;
import co.uk.wolfnotsheep.llmworker.backend.LlmService;
import co.uk.wolfnotsheep.llmworker.jobs.JobAcquisition;
import co.uk.wolfnotsheep.llmworker.jobs.JobStore;
import co.uk.wolfnotsheep.llmworker.model.ClassifyRequest;
import co.uk.wolfnotsheep.llmworker.model.ClassifyRequestText;
import co.uk.wolfnotsheep.llmworker.model.ClassifyRequestTextOneOf;
import co.uk.wolfnotsheep.llmworker.model.ClassifyRequestTextOneOf1;
import co.uk.wolfnotsheep.llmworker.model.ClassifyResponse;
import co.uk.wolfnotsheep.llmworker.model.JobAccepted;
import co.uk.wolfnotsheep.llmworker.model.PromptBlockRef;
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
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

/**
 * Implements {@link ClassifyApi}. Sync (200) without
 * {@code Prefer: respond-async}, 202 with poll URL when the header is
 * set (CSV #47). Sync and async share the {@link JobStore} row for
 * idempotency — same shape as {@code gls-classifier-router} and
 * {@code gls-extraction-audio}.
 */
@RestController
public class ClassifyController implements ClassifyApi {

    private static final Logger log = LoggerFactory.getLogger(ClassifyController.class);

    private final LlmService backend;
    private final JobStore jobs;
    private final ClassifyMetrics metrics;
    private final ObjectMapper mapper;
    private final ObjectProvider<AuditEmitter> auditEmitterProvider;
    private final AsyncDispatcher asyncDispatcher;
    private final String serviceName;
    private final String serviceVersion;
    private final String instanceId;

    public ClassifyController(
            LlmService backend,
            JobStore jobs,
            ClassifyMetrics metrics,
            ObjectMapper mapper,
            ObjectProvider<AuditEmitter> auditEmitterProvider,
            AsyncDispatcher asyncDispatcher,
            @Value("${spring.application.name:gls-llm-worker}") String serviceName,
            @Value("${gls.llm.worker.build.version:0.0.1-SNAPSHOT}") String serviceVersion,
            @Value("${HOSTNAME:unknown}") String instanceId) {
        this.backend = backend;
        this.jobs = jobs;
        this.metrics = metrics;
        this.mapper = mapper.copy()
                .addMixIn(ClassifyRequestText.class, ClassifyRequestTextMixin.class);
        this.auditEmitterProvider = auditEmitterProvider;
        this.asyncDispatcher = asyncDispatcher;
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
                    : handleSyncAcquired(traceparent, request);
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
        return (ResponseEntity<ClassifyResponse>) (ResponseEntity<?>)
                ResponseEntity.accepted().header(HttpHeaders.LOCATION, poll.toString()).body(body);
    }

    private ResponseEntity<ClassifyResponse> handleSyncAcquired(
            String traceparent, ClassifyRequest request) {
        io.micrometer.core.instrument.Timer.Sample timer = metrics.startTimer();
        try {
            jobs.markRunning(request.getNodeRunId());
            ClassifyResponse body = doClassify(traceparent, request);
            cacheCompleted(request.getNodeRunId(), body);
            metrics.recordSuccess(timer, "anthropic");
            return ResponseEntity.ok(body);
        } catch (RuntimeException failure) {
            String code = errorCodeFor(failure);
            metrics.recordFailure(timer, code);
            jobs.markFailed(request.getNodeRunId(), code, safeMessage(failure));
            emitFailed(request, traceparent, failure, code);
            throw failure;
        }
    }

    private ResponseEntity<ClassifyResponse> handleAsyncAcquired(
            String traceparent, ClassifyRequest request) {
        metrics.recordIdempotencyShortCircuit("async_dispatched");
        asyncDispatcher.dispatch(request, traceparent);
        return acceptedFor(request.getNodeRunId());
    }

    /** Background path. Package-private so {@link AsyncDispatcher} can {@code @Async}-invoke it. */
    void runAsync(ClassifyRequest request, String traceparent) {
        try {
            jobs.markRunning(request.getNodeRunId());
            ClassifyResponse body = doClassify(traceparent, request);
            cacheCompleted(request.getNodeRunId(), body);
        } catch (RuntimeException failure) {
            String code = errorCodeFor(failure);
            jobs.markFailed(request.getNodeRunId(), code, safeMessage(failure));
            emitFailed(request, traceparent, failure, code);
        }
    }

    private ClassifyResponse doClassify(String traceparent, ClassifyRequest request) {
        PromptBlockRef block = request.getBlock();
        String blockId = block == null ? null : block.getId();
        Integer blockVersion = block == null ? null : block.getVersion();
        String text = inlineText(request.getText());
        long byteCount = text == null ? 0L : text.getBytes(StandardCharsets.UTF_8).length;

        Instant started = Instant.now();
        LlmResult result = backend.classify(blockId, blockVersion, text);
        long durationMs = Duration.between(started, Instant.now()).toMillis();

        emitCompleted(request, traceparent, result, byteCount, durationMs);
        return toApi(request, result, byteCount, durationMs);
    }

    private static ClassifyResponse toApi(ClassifyRequest request, LlmResult result,
                                          long byteCount, long durationMs) {
        ClassifyResponse response = new ClassifyResponse();
        response.setNodeRunId(request.getNodeRunId());
        if (result.modelId() != null) response.setModelId(result.modelId());
        response.setConfidence(result.confidence());
        response.setResult(result.result());
        if (result.rationale() != null) response.setRationale(result.rationale());
        response.setDurationMs((int) Math.min(Integer.MAX_VALUE, durationMs));
        long costUnits = (result.tokensIn() + result.tokensOut() + 999) / 1000;
        response.setCostUnits((int) Math.min(Integer.MAX_VALUE, Math.max(0L, costUnits)));
        response.setTokensIn((int) Math.min(Integer.MAX_VALUE, Math.max(0L, result.tokensIn())));
        response.setTokensOut((int) Math.min(Integer.MAX_VALUE, Math.max(0L, result.tokensOut())));
        return response;
    }

    private static String inlineText(ClassifyRequestText text) {
        if (text instanceof ClassifyRequestTextOneOf inline) {
            return inline.getText() == null ? "" : inline.getText();
        }
        // textRef branch — Phase 1.5 first cut doesn't fetch.
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
            log.warn("llm cache write failed for nodeRunId={}: {}", nodeRunId, e.getMessage());
        }
    }

    private void emitCompleted(ClassifyRequest request, String traceparent,
                               LlmResult result, long byteCount, long durationMs) {
        AuditEmitter emitter = auditEmitterProvider.getIfAvailable();
        if (emitter == null) return;
        PromptBlockRef block = request.getBlock();
        try {
            emitter.emit(LlmEvents.completed(
                    serviceName, serviceVersion, instanceId,
                    request.getNodeRunId(), traceparent,
                    block == null ? null : block.getId(),
                    block == null ? null : block.getVersion(),
                    result.backend() == null ? null : result.backend().name(),
                    result.modelId(), result.confidence(),
                    byteCount, result.tokensIn(), result.tokensOut(), durationMs));
        } catch (RuntimeException e) {
            log.warn("audit emit (LLM_COMPLETED) failed for nodeRunId={}: {}",
                    request.getNodeRunId(), e.getMessage());
        }
    }

    private void emitFailed(ClassifyRequest request, String traceparent,
                            Throwable cause, String errorCode) {
        AuditEmitter emitter = auditEmitterProvider.getIfAvailable();
        if (emitter == null) return;
        String nodeRunId = request != null ? request.getNodeRunId() : null;
        try {
            emitter.emit(LlmEvents.failed(
                    serviceName, serviceVersion, instanceId,
                    nodeRunId, traceparent, errorCode, cause.getMessage()));
        } catch (RuntimeException e) {
            log.warn("audit emit (LLM_FAILED) failed for nodeRunId={}: {}",
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

    private static String errorCodeFor(Throwable cause) {
        if (cause instanceof LlmNotConfiguredException) return "LLM_NOT_CONFIGURED";
        if (cause instanceof BlockUnknownException) return "LLM_BLOCK_UNKNOWN";
        if (cause instanceof JobInFlightException) return "IDEMPOTENCY_IN_FLIGHT";
        if (cause instanceof java.io.UncheckedIOException) return "LLM_DEPENDENCY_UNAVAILABLE";
        return "LLM_UNEXPECTED";
    }
}
