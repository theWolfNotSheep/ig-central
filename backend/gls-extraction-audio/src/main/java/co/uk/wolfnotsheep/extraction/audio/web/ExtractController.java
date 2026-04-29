package co.uk.wolfnotsheep.extraction.audio.web;

import co.uk.wolfnotsheep.extraction.audio.api.ExtractionApi;
import co.uk.wolfnotsheep.extraction.audio.audit.AudioEvents;
import co.uk.wolfnotsheep.extraction.audio.jobs.JobAcquisition;
import co.uk.wolfnotsheep.extraction.audio.jobs.JobRecord;
import co.uk.wolfnotsheep.extraction.audio.jobs.JobStore;
import co.uk.wolfnotsheep.extraction.audio.model.ExtractRequest;
import co.uk.wolfnotsheep.extraction.audio.model.ExtractResponse;
import co.uk.wolfnotsheep.extraction.audio.model.ExtractResponseText;
import co.uk.wolfnotsheep.extraction.audio.model.ExtractResponseTextOneOf;
import co.uk.wolfnotsheep.extraction.audio.model.ExtractResponseTextOneOf1;
import co.uk.wolfnotsheep.extraction.audio.model.JobAccepted;
import co.uk.wolfnotsheep.extraction.audio.parse.AudioCorruptException;
import co.uk.wolfnotsheep.extraction.audio.parse.AudioNotConfiguredException;
import co.uk.wolfnotsheep.extraction.audio.parse.AudioResult;
import co.uk.wolfnotsheep.extraction.audio.parse.AudioTranscriptionService;
import co.uk.wolfnotsheep.extraction.audio.sink.DocumentSink;
import co.uk.wolfnotsheep.extraction.audio.sink.ExtractedTextRef;
import co.uk.wolfnotsheep.extraction.audio.source.DocumentEtagMismatchException;
import co.uk.wolfnotsheep.extraction.audio.source.DocumentNotFoundException;
import co.uk.wolfnotsheep.extraction.audio.source.DocumentRef;
import co.uk.wolfnotsheep.extraction.audio.source.DocumentSource;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

/**
 * Implements {@link ExtractionApi}. Sync (200) without
 * {@code Prefer: respond-async}, 202 with poll URL when the header
 * is set (CSV #47). Sync and async share the {@link JobStore} row
 * for idempotency.
 */
@RestController
public class ExtractController implements ExtractionApi {

    private static final Logger log = LoggerFactory.getLogger(ExtractController.class);
    private static final int BYTES_PER_COST_UNIT = 1024;

    private final DocumentSource source;
    private final AudioTranscriptionService backend;
    private final DocumentSink sink;
    private final JobStore jobs;
    private final ExtractMetrics metrics;
    private final ObjectMapper mapper;
    private final ObjectProvider<AuditEmitter> auditEmitterProvider;
    private final long inlineByteCeiling;
    private final long maxSourceBytes;
    private final String serviceName;
    private final String serviceVersion;
    private final String instanceId;
    private final AsyncDispatcher asyncDispatcher;

    public ExtractController(
            DocumentSource source,
            AudioTranscriptionService backend,
            DocumentSink sink,
            JobStore jobs,
            ExtractMetrics metrics,
            ObjectMapper mapper,
            ObjectProvider<AuditEmitter> auditEmitterProvider,
            AsyncDispatcher asyncDispatcher,
            @Value("${gls.extraction.audio.inline-byte-ceiling:262144}") long inlineByteCeiling,
            @Value("${gls.extraction.audio.max-source-bytes:524288000}") long maxSourceBytes,
            @Value("${spring.application.name:gls-extraction-audio}") String serviceName,
            @Value("${gls.extraction.audio.build.version:0.0.1-SNAPSHOT}") String serviceVersion,
            @Value("${HOSTNAME:unknown}") String instanceId) {
        this.source = source;
        this.backend = backend;
        this.sink = sink;
        this.jobs = jobs;
        this.metrics = metrics;
        this.mapper = mapper.copy()
                .addMixIn(ExtractResponseText.class, ExtractResponseTextMixin.class);
        this.auditEmitterProvider = auditEmitterProvider;
        this.asyncDispatcher = asyncDispatcher;
        this.inlineByteCeiling = inlineByteCeiling;
        this.maxSourceBytes = maxSourceBytes;
        this.serviceName = serviceName;
        this.serviceVersion = serviceVersion;
        this.instanceId = instanceId;
    }

    @Override
    public ResponseEntity<ExtractResponse> extractAudio(
            String traceparent, ExtractRequest request, String idempotencyKey, String prefer) {

        boolean async = prefer != null && prefer.toLowerCase().contains("respond-async");
        String bucket = request.getDocumentRef() == null
                ? null : request.getDocumentRef().getBucket();

        JobAcquisition acq = jobs.tryAcquire(request.getNodeRunId());
        return switch (acq.status()) {
            case ACQUIRED -> async
                    ? handleAsyncAcquired(traceparent, request, bucket)
                    : handleSyncAcquired(traceparent, request, bucket);
            case RUNNING -> async
                    ? acceptedFor(request.getNodeRunId())
                    : runningCollision(request.getNodeRunId(), bucket);
            case COMPLETED -> {
                metrics.recordIdempotencyShortCircuit("cached", bucket);
                if (async) {
                    yield acceptedFor(request.getNodeRunId());
                }
                yield ResponseEntity.ok(deserialiseCached(acq.existing().resultJson()));
            }
            case FAILED -> async
                    ? acceptedFor(request.getNodeRunId())
                    : runningCollision(request.getNodeRunId(), bucket);
        };
    }

    private ResponseEntity<ExtractResponse> runningCollision(String nodeRunId, String bucket) {
        metrics.recordIdempotencyShortCircuit("in_flight", bucket);
        throw new JobInFlightException(nodeRunId);
    }

    @SuppressWarnings("unchecked")
    private ResponseEntity<ExtractResponse> acceptedFor(String nodeRunId) {
        URI poll = URI.create("/v1/jobs/" + nodeRunId);
        JobAccepted body = new JobAccepted();
        body.setNodeRunId(nodeRunId);
        body.setStatus(JobAccepted.StatusEnum.PENDING);
        body.setPollUrl(poll);
        // The contract advertises 200 returning ExtractResponse and 202
        // returning JobAccepted. The generated interface narrows the
        // return type to ExtractResponse, so the 202 body lands as a
        // raw JobAccepted via a cast — ResponseEntity.body is Object
        // at runtime; Jackson serialises JobAccepted's fields cleanly.
        return (ResponseEntity<ExtractResponse>) (ResponseEntity<?>)
                ResponseEntity.accepted().header(HttpHeaders.LOCATION, poll.toString()).body(body);
    }

    private ResponseEntity<ExtractResponse> handleSyncAcquired(
            String traceparent, ExtractRequest request, String bucket) {
        io.micrometer.core.instrument.Timer.Sample timer = metrics.startTimer();
        try {
            jobs.markRunning(request.getNodeRunId());
            ExtractResponse body = doTranscribe(traceparent, request);
            cacheCompleted(request.getNodeRunId(), body);
            metrics.recordSuccess(timer, bucket, backend.providerId(), approxByteCount(body));
            return ResponseEntity.ok(body);
        } catch (RuntimeException failure) {
            String code = errorCodeFor(failure);
            metrics.recordFailure(timer, bucket, code);
            jobs.markFailed(request.getNodeRunId(), code, safeMessage(failure));
            emitFailed(request, traceparent, failure, code);
            throw failure;
        }
    }

    private ResponseEntity<ExtractResponse> handleAsyncAcquired(
            String traceparent, ExtractRequest request, String bucket) {
        metrics.recordIdempotencyShortCircuit("async_dispatched", bucket);
        asyncDispatcher.dispatch(request, traceparent);
        return acceptedFor(request.getNodeRunId());
    }

    /** Background path. Public so it can be {@code @Async}-invoked from {@link AsyncDispatcher}. */
    void runAsync(ExtractRequest request, String traceparent) {
        try {
            jobs.markRunning(request.getNodeRunId());
            ExtractResponse body = doTranscribe(traceparent, request);
            cacheCompleted(request.getNodeRunId(), body);
        } catch (RuntimeException failure) {
            String code = errorCodeFor(failure);
            jobs.markFailed(request.getNodeRunId(), code, safeMessage(failure));
            emitFailed(request, traceparent, failure, code);
        }
    }

    private ExtractResponse doTranscribe(String traceparent, ExtractRequest request) {
        DocumentRef ref = toInternalRef(request);
        Instant started = Instant.now();
        long sourceSize = source.sizeOf(ref);
        if (sourceSize > maxSourceBytes) {
            throw new DocumentTooLargeException(
                    "source size " + sourceSize + " exceeds cap " + maxSourceBytes);
        }
        AudioResult result;
        try (InputStream stream = source.open(ref)) {
            String fileName = request.getDocumentRef().getObjectKey();
            result = backend.transcribe(stream, fileName, sourceSize,
                    request.getLanguage(), request.getPrompt());
        } catch (UncheckedIOException ioWrap) {
            throw ioWrap;
        } catch (RuntimeException rethrow) {
            throw rethrow;
        } catch (Exception e) {
            throw new UncheckedIOException("Failed to close source stream", new IOException(e));
        }
        long textBytes = result.text().getBytes(StandardCharsets.UTF_8).length;
        long durationMs = Duration.between(started, Instant.now()).toMillis();
        emitCompleted(request, traceparent, result, durationMs);
        return textBytes > inlineByteCeiling
                ? buildRefResponse(request, result, durationMs)
                : buildInlineResponse(request, result, durationMs);
    }

    private ExtractResponse buildInlineResponse(ExtractRequest request, AudioResult result, long durationMs) {
        ExtractResponseTextOneOf inline = new ExtractResponseTextOneOf();
        inline.setText(result.text());
        inline.setEncoding(ExtractResponseTextOneOf.EncodingEnum.UTF_8);
        return commonResponse(request, result, durationMs, inline);
    }

    private ExtractResponse buildRefResponse(ExtractRequest request, AudioResult result, long durationMs) {
        ExtractedTextRef ref = sink.upload(request.getNodeRunId(), result.text());
        ExtractResponseTextOneOf1 byRef = new ExtractResponseTextOneOf1();
        byRef.setTextRef(ref.uri());
        byRef.setContentLength(ref.contentLength());
        byRef.setContentType(ref.contentType());
        return commonResponse(request, result, durationMs, byRef);
    }

    private static ExtractResponse commonResponse(
            ExtractRequest request, AudioResult result, long durationMs, ExtractResponseText text) {
        ExtractResponse response = new ExtractResponse();
        response.setNodeRunId(request.getNodeRunId());
        response.setText(text);
        response.setDetectedMimeType(result.detectedMimeType());
        response.setLanguage(result.language());
        response.setDurationSeconds(result.durationSeconds());
        response.setProvider(result.provider());
        response.setDurationMs((int) Math.min(Integer.MAX_VALUE, durationMs));
        response.setCostUnits((int) Math.min(Integer.MAX_VALUE,
                Math.max(0L, result.byteCount() / BYTES_PER_COST_UNIT)));
        return response;
    }

    private ExtractResponse deserialiseCached(String json) {
        try {
            return mapper.readValue(json, ExtractResponse.class);
        } catch (JsonProcessingException e) {
            log.warn("idempotency cache deserialise failed: {}", e.getMessage());
            throw new IllegalStateException("idempotency cache row was unparseable", e);
        }
    }

    private void cacheCompleted(String nodeRunId, ExtractResponse response) {
        if (response == null) return;
        try {
            String json = mapper.writeValueAsString(response);
            jobs.markCompleted(nodeRunId, json);
        } catch (JsonProcessingException e) {
            log.warn("audio cache write failed for nodeRunId={}: {}", nodeRunId, e.getMessage());
        }
    }

    private void emitCompleted(ExtractRequest request, String traceparent,
                               AudioResult result, long durationMs) {
        AuditEmitter emitter = auditEmitterProvider.getIfAvailable();
        if (emitter == null) return;
        try {
            emitter.emit(AudioEvents.completed(
                    serviceName, serviceVersion, instanceId,
                    request.getNodeRunId(), traceparent,
                    result.detectedMimeType(), result.provider(),
                    result.language(), result.durationSeconds(),
                    result.byteCount(), durationMs));
        } catch (RuntimeException e) {
            log.warn("audit emit (EXTRACTION_COMPLETED) failed for nodeRunId={}: {}",
                    request.getNodeRunId(), e.getMessage());
        }
    }

    private void emitFailed(ExtractRequest request, String traceparent,
                            Throwable cause, String errorCode) {
        AuditEmitter emitter = auditEmitterProvider.getIfAvailable();
        if (emitter == null) return;
        String nodeRunId = request != null ? request.getNodeRunId() : null;
        try {
            emitter.emit(AudioEvents.failed(
                    serviceName, serviceVersion, instanceId,
                    nodeRunId, traceparent, errorCode, cause.getMessage()));
        } catch (RuntimeException e) {
            log.warn("audit emit (EXTRACTION_FAILED) failed for nodeRunId={}: {}",
                    nodeRunId, e.getMessage());
        }
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.DEDUCTION)
    @JsonSubTypes({
            @JsonSubTypes.Type(ExtractResponseTextOneOf.class),
            @JsonSubTypes.Type(ExtractResponseTextOneOf1.class)
    })
    abstract static class ExtractResponseTextMixin {
    }

    private static String errorCodeFor(Throwable cause) {
        if (cause instanceof DocumentNotFoundException) return "DOCUMENT_NOT_FOUND";
        if (cause instanceof DocumentEtagMismatchException) return "DOCUMENT_ETAG_MISMATCH";
        if (cause instanceof AudioCorruptException) return "AUDIO_CORRUPT";
        if (cause instanceof AudioNotConfiguredException) return "AUDIO_NOT_CONFIGURED";
        if (cause instanceof DocumentTooLargeException) return "AUDIO_TOO_LARGE";
        if (cause instanceof JobInFlightException) return "IDEMPOTENCY_IN_FLIGHT";
        if (cause instanceof UncheckedIOException) return "AUDIO_SOURCE_UNAVAILABLE";
        return "AUDIO_UNEXPECTED";
    }

    private static String safeMessage(Throwable t) {
        return t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
    }

    private static DocumentRef toInternalRef(ExtractRequest request) {
        var generated = request.getDocumentRef();
        String etag = generated.getEtag();
        return etag == null || etag.isBlank()
                ? DocumentRef.of(generated.getBucket(), generated.getObjectKey())
                : DocumentRef.withEtag(generated.getBucket(), generated.getObjectKey(), etag);
    }

    private static long approxByteCount(ExtractResponse body) {
        return body.getCostUnits() == null ? 0L : ((long) body.getCostUnits()) * BYTES_PER_COST_UNIT;
    }
}
