package co.uk.wolfnotsheep.bert.web;

import co.uk.wolfnotsheep.bert.api.InferenceApi;
import co.uk.wolfnotsheep.bert.audit.BertEvents;
import co.uk.wolfnotsheep.bert.inference.BlockUnknownException;
import co.uk.wolfnotsheep.bert.inference.InferenceEngine;
import co.uk.wolfnotsheep.bert.inference.InferenceResult;
import co.uk.wolfnotsheep.bert.inference.ModelNotLoadedException;
import co.uk.wolfnotsheep.bert.model.BertBlockRef;
import co.uk.wolfnotsheep.bert.model.InferRequest;
import co.uk.wolfnotsheep.bert.model.InferRequestText;
import co.uk.wolfnotsheep.bert.model.InferRequestTextOneOf;
import co.uk.wolfnotsheep.bert.model.InferRequestTextOneOf1;
import co.uk.wolfnotsheep.bert.model.InferResponse;
import co.uk.wolfnotsheep.bert.model.InferResponseScoresInner;
import co.uk.wolfnotsheep.platformaudit.emit.AuditEmitter;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
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
 * Implements {@link InferenceApi}. Phase 1.4 PR1 wires only the
 * stub backend; the real DJL impl swaps in behind the same
 * {@link InferenceEngine} interface.
 */
@RestController
public class InferController implements InferenceApi {

    private static final Logger log = LoggerFactory.getLogger(InferController.class);
    private static final int BYTES_PER_COST_UNIT = 1024;

    private final InferenceEngine engine;
    private final InferMetrics metrics;
    private final ObjectProvider<AuditEmitter> auditEmitterProvider;
    private final String serviceName;
    private final String serviceVersion;
    private final String instanceId;

    public InferController(
            InferenceEngine engine,
            InferMetrics metrics,
            ObjectProvider<AuditEmitter> auditEmitterProvider,
            @Value("${spring.application.name:gls-bert-inference}") String serviceName,
            @Value("${gls.bert.build.version:0.0.1-SNAPSHOT}") String serviceVersion,
            @Value("${HOSTNAME:unknown}") String instanceId) {
        this.engine = engine;
        this.metrics = metrics;
        this.auditEmitterProvider = auditEmitterProvider;
        this.serviceName = serviceName;
        this.serviceVersion = serviceVersion;
        this.instanceId = instanceId;
    }

    @Override
    public ResponseEntity<InferResponse> infer(String traceparent, InferRequest request) {
        BertBlockRef block = request.getBlock();
        String blockId = block == null ? null : block.getId();
        Integer blockVersion = block == null ? null : block.getVersion();
        String text = inlineText(request.getText());
        String nodeRunId = request.getNodeRunId();

        var timer = metrics.startTimer();
        Instant started = Instant.now();
        InferenceResult result;
        try {
            result = engine.infer(blockId, blockVersion, text);
        } catch (RuntimeException failure) {
            String code = errorCodeFor(failure);
            metrics.recordFailure(timer, code);
            emitFailed(nodeRunId, traceparent, code, failure);
            throw failure;
        }
        long durationMs = Duration.between(started, Instant.now()).toMillis();

        emitCompleted(nodeRunId, traceparent, blockId, blockVersion, result, durationMs);
        metrics.recordSuccess(timer, result.modelVersion(), result.byteCount());

        return ResponseEntity.ok(toApi(result, durationMs));
    }

    private static InferResponse toApi(InferenceResult r, long durationMs) {
        InferResponse body = new InferResponse();
        body.setLabel(r.label());
        body.setConfidence(r.confidence());
        body.setModelVersion(r.modelVersion());
        if (r.scores() != null && !r.scores().isEmpty()) {
            List<InferResponseScoresInner> scores = new ArrayList<>(r.scores().size());
            for (InferenceResult.LabelScore s : r.scores()) {
                InferResponseScoresInner inner = new InferResponseScoresInner();
                inner.setLabel(s.label());
                inner.setConfidence(s.confidence());
                scores.add(inner);
            }
            body.setScores(scores);
        }
        body.setDurationMs((int) Math.min(Integer.MAX_VALUE, durationMs));
        body.setCostUnits((int) Math.min(Integer.MAX_VALUE,
                Math.max(0L, r.byteCount() / BYTES_PER_COST_UNIT)));
        return body;
    }

    private static String inlineText(InferRequestText text) {
        if (text instanceof InferRequestTextOneOf inline) {
            return inline.getText() == null ? "" : inline.getText();
        }
        // textRef branch — Phase 1.4 PR1 returns the stub which doesn't
        // inspect the text. The real engine will add a MinIO fetcher
        // when it ships; same pattern as the router's textRef path.
        return "";
    }

    private void emitCompleted(String nodeRunId, String traceparent,
                               String blockId, Integer blockVersion,
                               InferenceResult result, long durationMs) {
        AuditEmitter emitter = auditEmitterProvider.getIfAvailable();
        if (emitter == null) return;
        try {
            emitter.emit(BertEvents.completed(
                    serviceName, serviceVersion, instanceId,
                    nodeRunId, traceparent,
                    blockId, blockVersion, result.modelVersion(),
                    result.label(), result.confidence(),
                    result.byteCount(), durationMs));
        } catch (RuntimeException e) {
            log.warn("audit emit (INFER_COMPLETED) failed for nodeRunId={}: {}",
                    nodeRunId, e.getMessage());
        }
    }

    private void emitFailed(String nodeRunId, String traceparent,
                            String errorCode, Throwable cause) {
        AuditEmitter emitter = auditEmitterProvider.getIfAvailable();
        if (emitter == null) return;
        try {
            emitter.emit(BertEvents.failed(
                    serviceName, serviceVersion, instanceId,
                    nodeRunId, traceparent, errorCode, cause.getMessage()));
        } catch (RuntimeException e) {
            log.warn("audit emit (INFER_FAILED) failed for nodeRunId={}: {}",
                    nodeRunId, e.getMessage());
        }
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.DEDUCTION)
    @JsonSubTypes({
            @JsonSubTypes.Type(InferRequestTextOneOf.class),
            @JsonSubTypes.Type(InferRequestTextOneOf1.class)
    })
    abstract static class InferRequestTextMixin {
    }

    private static String errorCodeFor(Throwable cause) {
        if (cause instanceof ModelNotLoadedException) return "MODEL_NOT_LOADED";
        if (cause instanceof BlockUnknownException) return "BLOCK_UNKNOWN";
        if (cause instanceof java.io.UncheckedIOException) return "BERT_DEPENDENCY_UNAVAILABLE";
        return "BERT_UNEXPECTED";
    }
}
