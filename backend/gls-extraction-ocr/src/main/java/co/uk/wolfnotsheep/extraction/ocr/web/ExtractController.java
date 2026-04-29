package co.uk.wolfnotsheep.extraction.ocr.web;

import co.uk.wolfnotsheep.extraction.ocr.api.ExtractionApi;
import co.uk.wolfnotsheep.extraction.ocr.audit.OcrEvents;
import co.uk.wolfnotsheep.extraction.ocr.idempotency.IdempotencyInFlightException;
import co.uk.wolfnotsheep.extraction.ocr.idempotency.IdempotencyOutcome;
import co.uk.wolfnotsheep.extraction.ocr.idempotency.IdempotencyStore;
import co.uk.wolfnotsheep.extraction.ocr.model.ExtractRequest;
import co.uk.wolfnotsheep.extraction.ocr.model.ExtractResponse;
import co.uk.wolfnotsheep.extraction.ocr.model.ExtractResponseText;
import co.uk.wolfnotsheep.extraction.ocr.model.ExtractResponseTextOneOf;
import co.uk.wolfnotsheep.extraction.ocr.model.ExtractResponseTextOneOf1;
import co.uk.wolfnotsheep.extraction.ocr.parse.OcrExtractionService;
import co.uk.wolfnotsheep.extraction.ocr.parse.OcrLanguageUnsupportedException;
import co.uk.wolfnotsheep.extraction.ocr.parse.OcrResult;
import co.uk.wolfnotsheep.extraction.ocr.parse.UnparseableImageException;
import co.uk.wolfnotsheep.extraction.ocr.sink.DocumentSink;
import co.uk.wolfnotsheep.extraction.ocr.sink.ExtractedTextRef;
import co.uk.wolfnotsheep.extraction.ocr.source.DocumentEtagMismatchException;
import co.uk.wolfnotsheep.extraction.ocr.source.DocumentNotFoundException;
import co.uk.wolfnotsheep.extraction.ocr.source.DocumentRef;
import co.uk.wolfnotsheep.extraction.ocr.source.DocumentSource;
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

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Implements {@link ExtractionApi} for the OCR service. Mirrors the
 * Tika service's controller orchestration: idempotency → source.open →
 * tesseract.run → response shape (inline text or textRef per CSV #19)
 * → cache + audit.
 */
@RestController
public class ExtractController implements ExtractionApi {

    private static final Logger log = LoggerFactory.getLogger(ExtractController.class);
    private static final int BYTES_PER_COST_UNIT = 1024;

    private final DocumentSource source;
    private final OcrExtractionService ocr;
    private final DocumentSink sink;
    private final IdempotencyStore idempotency;
    private final ExtractMetrics metrics;
    private final ObjectMapper mapper;
    private final ObjectProvider<AuditEmitter> auditEmitterProvider;
    private final long inlineByteCeiling;
    private final long maxSourceBytes;
    private final String serviceName;
    private final String serviceVersion;
    private final String instanceId;

    public ExtractController(
            DocumentSource source,
            OcrExtractionService ocr,
            DocumentSink sink,
            IdempotencyStore idempotency,
            ExtractMetrics metrics,
            ObjectMapper mapper,
            ObjectProvider<AuditEmitter> auditEmitterProvider,
            @Value("${gls.extraction.ocr.inline-byte-ceiling:262144}") long inlineByteCeiling,
            @Value("${gls.extraction.ocr.max-source-bytes:268435456}") long maxSourceBytes,
            @Value("${spring.application.name:gls-extraction-ocr}") String serviceName,
            @Value("${gls.extraction.ocr.build.version:0.0.1-SNAPSHOT}") String serviceVersion,
            @Value("${HOSTNAME:unknown}") String instanceId) {
        this.source = source;
        this.ocr = ocr;
        this.sink = sink;
        this.idempotency = idempotency;
        this.metrics = metrics;
        // Same Jackson DEDUCTION mixin pattern as Tika's controller —
        // the generated `oneOf` interface has no discriminator so
        // cached-response deserialise picks the subtype by which
        // properties are present.
        this.mapper = mapper.copy()
                .addMixIn(ExtractResponseText.class, ExtractResponseTextMixin.class);
        this.auditEmitterProvider = auditEmitterProvider;
        this.inlineByteCeiling = inlineByteCeiling;
        this.maxSourceBytes = maxSourceBytes;
        this.serviceName = serviceName;
        this.serviceVersion = serviceVersion;
        this.instanceId = instanceId;
    }

    @Override
    public ResponseEntity<ExtractResponse> extractOcr(
            String traceparent, ExtractRequest request, String idempotencyKey) {

        String bucket = request.getDocumentRef() == null
                ? null : request.getDocumentRef().getBucket();

        IdempotencyOutcome outcome = idempotency.tryAcquire(request.getNodeRunId());
        switch (outcome.status()) {
            case CACHED -> {
                metrics.recordIdempotencyShortCircuit("cached", bucket);
                return ResponseEntity.ok(deserialiseCached(outcome.cachedJson()));
            }
            case IN_FLIGHT -> {
                metrics.recordIdempotencyShortCircuit("in_flight", bucket);
                throw new IdempotencyInFlightException(request.getNodeRunId());
            }
            case ACQUIRED -> { /* fall through */ }
        }

        io.micrometer.core.instrument.Timer.Sample timer = metrics.startTimer();
        try {
            ResponseEntity<ExtractResponse> response = doExtract(traceparent, request, idempotencyKey);
            ExtractResponse body = response.getBody();
            cacheCompleted(request.getNodeRunId(), body);
            metrics.recordSuccess(timer, bucket,
                    body == null ? null : body.getDetectedMimeType(),
                    body == null ? 0L : approxByteCount(body));
            return response;
        } catch (RuntimeException failure) {
            metrics.recordFailure(timer, bucket, errorCodeFor(failure));
            idempotency.releaseOnFailure(request.getNodeRunId());
            emitFailed(request, traceparent, failure);
            throw failure;
        }
    }

    private ResponseEntity<ExtractResponse> doExtract(
            String traceparent, ExtractRequest request, String idempotencyKey) {

        DocumentRef ref = toInternalRef(request);
        Instant started = Instant.now();
        if (idempotencyKey != null) {
            log.debug("ocr: nodeRunId={} idempotencyKey={}",
                    request.getNodeRunId(), idempotencyKey);
        }

        long sourceSize = source.sizeOf(ref);
        if (sourceSize > maxSourceBytes) {
            throw new DocumentTooLargeException(
                    "source size " + sourceSize + " exceeds cap " + maxSourceBytes);
        }

        OcrResult result;
        try (InputStream stream = source.open(ref)) {
            String fileName = request.getDocumentRef().getObjectKey();
            result = ocr.run(stream, fileName, request.getLanguages());
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

        ExtractResponse response = textBytes > inlineByteCeiling
                ? buildRefResponse(request, result, durationMs)
                : buildInlineResponse(request, result, durationMs);
        return ResponseEntity.ok(response);
    }

    private ExtractResponse buildInlineResponse(
            ExtractRequest request, OcrResult result, long durationMs) {
        ExtractResponseTextOneOf inline = new ExtractResponseTextOneOf();
        inline.setText(result.text());
        inline.setEncoding(ExtractResponseTextOneOf.EncodingEnum.UTF_8);
        return commonResponse(request, result, durationMs, inline);
    }

    private ExtractResponse buildRefResponse(
            ExtractRequest request, OcrResult result, long durationMs) {
        ExtractedTextRef ref = sink.upload(request.getNodeRunId(), result.text());
        ExtractResponseTextOneOf1 byRef = new ExtractResponseTextOneOf1();
        byRef.setTextRef(ref.uri());
        byRef.setContentLength(ref.contentLength());
        byRef.setContentType(ref.contentType());
        return commonResponse(request, result, durationMs, byRef);
    }

    private static ExtractResponse commonResponse(
            ExtractRequest request, OcrResult result, long durationMs, ExtractResponseText text) {
        ExtractResponse response = new ExtractResponse();
        response.setNodeRunId(request.getNodeRunId());
        response.setText(text);
        response.setDetectedMimeType(result.detectedMimeType());
        response.setLanguages(result.languages());
        response.setPageCount(result.pageCount());
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
            idempotency.cacheResult(nodeRunId, json);
        } catch (JsonProcessingException e) {
            log.warn("idempotency cache write failed for nodeRunId={}: {}",
                    nodeRunId, e.getMessage());
        }
    }

    private void emitCompleted(ExtractRequest request, String traceparent,
                               OcrResult result, long durationMs) {
        AuditEmitter emitter = auditEmitterProvider.getIfAvailable();
        if (emitter == null) return;
        try {
            emitter.emit(OcrEvents.completed(
                    serviceName, serviceVersion, instanceId,
                    request.getNodeRunId(), traceparent,
                    result.detectedMimeType(),
                    result.languages(),
                    result.pageCount(),
                    result.byteCount(),
                    durationMs));
        } catch (RuntimeException e) {
            log.warn("audit emit (EXTRACTION_COMPLETED) failed for nodeRunId={}: {}",
                    request.getNodeRunId(), e.getMessage());
        }
    }

    private void emitFailed(ExtractRequest request, String traceparent, Throwable cause) {
        AuditEmitter emitter = auditEmitterProvider.getIfAvailable();
        if (emitter == null) return;
        String errorCode = errorCodeFor(cause);
        String nodeRunId = request != null ? request.getNodeRunId() : null;
        try {
            emitter.emit(OcrEvents.failed(
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
        if (cause instanceof UnparseableImageException) return "OCR_CORRUPT";
        if (cause instanceof OcrLanguageUnsupportedException) return "OCR_LANGUAGE_UNSUPPORTED";
        if (cause instanceof DocumentTooLargeException) return "OCR_TOO_LARGE";
        if (cause instanceof IdempotencyInFlightException) return "IDEMPOTENCY_IN_FLIGHT";
        if (cause instanceof UncheckedIOException) return "OCR_SOURCE_UNAVAILABLE";
        return "OCR_UNEXPECTED";
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
