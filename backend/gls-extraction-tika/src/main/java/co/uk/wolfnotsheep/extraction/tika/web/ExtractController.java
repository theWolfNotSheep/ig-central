package co.uk.wolfnotsheep.extraction.tika.web;

import co.uk.wolfnotsheep.extraction.tika.api.ExtractionApi;
import co.uk.wolfnotsheep.extraction.tika.audit.ExtractionEvents;
import co.uk.wolfnotsheep.extraction.tika.idempotency.IdempotencyInFlightException;
import co.uk.wolfnotsheep.extraction.tika.idempotency.IdempotencyOutcome;
import co.uk.wolfnotsheep.extraction.tika.idempotency.IdempotencyStore;
import co.uk.wolfnotsheep.extraction.tika.model.ExtractRequest;
import co.uk.wolfnotsheep.extraction.tika.model.ExtractResponse;
import co.uk.wolfnotsheep.extraction.tika.model.ExtractResponseText;
import co.uk.wolfnotsheep.extraction.tika.model.ExtractResponseTextOneOf;
import co.uk.wolfnotsheep.extraction.tika.model.ExtractResponseTextOneOf1;
import co.uk.wolfnotsheep.extraction.tika.parse.ExtractedText;
import co.uk.wolfnotsheep.extraction.tika.parse.TikaExtractionService;
import co.uk.wolfnotsheep.extraction.tika.sink.DocumentSink;
import co.uk.wolfnotsheep.extraction.tika.sink.ExtractedTextRef;
import co.uk.wolfnotsheep.extraction.tika.source.DocumentRef;
import co.uk.wolfnotsheep.extraction.tika.source.DocumentSource;
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

import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

/**
 * Implements the generated {@link ExtractionApi} interface from
 * {@code contracts/extraction/openapi.yaml}.
 *
 * <p>Pipeline: {@code source.open} → {@code tika.extract} → response
 * shape. Payloads above {@code inlineByteCeiling} are uploaded to a
 * MinIO sink and surfaced on the contract's {@code textRef} branch
 * per CSV #19; smaller payloads land inline. {@link DocumentTooLargeException}
 * is retained for future sink-side capacity caps but is not raised by
 * this controller today.
 *
 * <p>{@code nodeRunId} idempotency (CSV #16) is a follow-up — until then
 * the {@code Idempotency-Key} header is logged but not consulted.
 */
@RestController
public class ExtractController implements ExtractionApi {

    private static final Logger log = LoggerFactory.getLogger(ExtractController.class);
    private static final int BYTES_PER_COST_UNIT = 1024;

    private final DocumentSource source;
    private final TikaExtractionService tika;
    private final DocumentSink sink;
    private final IdempotencyStore idempotency;
    private final ObjectMapper mapper;
    private final ObjectProvider<AuditEmitter> auditEmitterProvider;
    private final long inlineByteCeiling;
    private final String serviceName;
    private final String serviceVersion;
    private final String instanceId;

    public ExtractController(
            DocumentSource source,
            TikaExtractionService tika,
            DocumentSink sink,
            IdempotencyStore idempotency,
            ObjectMapper mapper,
            ObjectProvider<AuditEmitter> auditEmitterProvider,
            @Value("${gls.extraction.tika.inline-byte-ceiling:262144}") long inlineByteCeiling,
            @Value("${spring.application.name:gls-extraction-tika}") String serviceName,
            @Value("${gls.extraction.build.version:0.0.1-SNAPSHOT}") String serviceVersion,
            @Value("${HOSTNAME:unknown}") String instanceId) {
        this.source = source;
        this.tika = tika;
        this.sink = sink;
        this.idempotency = idempotency;
        // The generated ExtractResponseText interface (oneOf) has no
        // Jackson discriminator. Without help, deserialising a cached
        // response can't pick between inline (text + encoding) and ref
        // (textRef + contentLength). DEDUCTION picks the subtype by
        // which properties are present — works because the two
        // branches share no required fields.
        this.mapper = mapper.copy()
                .addMixIn(ExtractResponseText.class, ExtractResponseTextMixin.class);
        this.auditEmitterProvider = auditEmitterProvider;
        this.inlineByteCeiling = inlineByteCeiling;
        this.serviceName = serviceName;
        this.serviceVersion = serviceVersion;
        this.instanceId = instanceId;
    }

    @Override
    public ResponseEntity<ExtractResponse> extractDocument(
            String traceparent,
            ExtractRequest request,
            String idempotencyKey) {

        // Idempotency check first — before any source I/O. CSV #16:
        // repeated requests with the same nodeRunId within the 24h TTL
        // return the cached result (200) if completed, or 409 IN_FLIGHT
        // if still running.
        IdempotencyOutcome outcome = idempotency.tryAcquire(request.getNodeRunId());
        switch (outcome.status()) {
            case CACHED -> {
                return ResponseEntity.ok(deserialiseCached(outcome.cachedJson()));
            }
            case IN_FLIGHT -> throw new IdempotencyInFlightException(request.getNodeRunId());
            case ACQUIRED -> { /* fall through to extraction */ }
        }

        try {
            ResponseEntity<ExtractResponse> response = doExtract(traceparent, request, idempotencyKey);
            cacheCompleted(request.getNodeRunId(), response.getBody());
            return response;
        } catch (RuntimeException failure) {
            idempotency.releaseOnFailure(request.getNodeRunId());
            // Failure-path audit. Emitted from the controller (rather
            // than from ExtractionExceptionHandler) so traceparent +
            // nodeRunId are still in scope without a request-scoped
            // bean. The handler still owns the RFC 7807 mapping.
            emitFailed(request, traceparent, failure);
            throw failure;
        }
    }

    private ExtractResponse deserialiseCached(String json) {
        try {
            return mapper.readValue(json, ExtractResponse.class);
        } catch (JsonProcessingException e) {
            // Cached row is corrupt — best to drop it and let the
            // caller retry, which will re-extract.
            log.warn("idempotency cache deserialise failed: {}", e.getMessage());
            throw new IllegalStateException("idempotency cache row was unparseable", e);
        }
    }

    private void cacheCompleted(String nodeRunId, ExtractResponse response) {
        if (response == null) {
            return;
        }
        try {
            String json = mapper.writeValueAsString(response);
            idempotency.cacheResult(nodeRunId, json);
        } catch (JsonProcessingException e) {
            // Don't fail the user-facing call because the cache write
            // failed — the response is correct, just unrepeatable on
            // a retry within the TTL window.
            log.warn("idempotency cache write failed for nodeRunId={}: {}",
                    nodeRunId, e.getMessage());
        }
    }

    private ResponseEntity<ExtractResponse> doExtract(
            String traceparent,
            ExtractRequest request,
            String idempotencyKey) {

        DocumentRef ref = toInternalRef(request);
        Instant started = Instant.now();
        if (idempotencyKey != null) {
            log.debug("extract: nodeRunId={} idempotencyKey={}",
                    request.getNodeRunId(), idempotencyKey);
        }

        ExtractedText extracted;
        try (InputStream stream = source.open(ref)) {
            String fileName = request.getDocumentRef().getObjectKey();
            extracted = tika.extract(stream, fileName);
        } catch (UncheckedIOException ioWrap) {
            // Source-side I/O — let the handler decide (5xx).
            throw ioWrap;
        } catch (RuntimeException rethrow) {
            throw rethrow;
        } catch (Exception e) {
            throw new UncheckedIOException("Failed to close source stream", new java.io.IOException(e));
        }

        long textBytes = extracted.text().getBytes(StandardCharsets.UTF_8).length;
        long durationMs = Duration.between(started, Instant.now()).toMillis();
        emitCompleted(request, traceparent, extracted, durationMs);

        ExtractResponse response = textBytes > inlineByteCeiling
                ? buildRefResponse(request, extracted, durationMs)
                : buildInlineResponse(request, extracted, durationMs);
        return ResponseEntity.ok(response);
    }

    private void emitFailed(ExtractRequest request, String traceparent, Throwable cause) {
        AuditEmitter emitter = auditEmitterProvider.getIfAvailable();
        if (emitter == null) {
            return;
        }
        String errorCode = errorCodeFor(cause);
        String nodeRunId = request != null ? request.getNodeRunId() : null;
        try {
            emitter.emit(ExtractionEvents.failed(
                    serviceName, serviceVersion, instanceId,
                    nodeRunId, traceparent,
                    errorCode, cause.getMessage()));
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
        if (cause instanceof co.uk.wolfnotsheep.extraction.tika.source.DocumentNotFoundException) {
            return "DOCUMENT_NOT_FOUND";
        }
        if (cause instanceof co.uk.wolfnotsheep.extraction.tika.source.DocumentEtagMismatchException) {
            return "DOCUMENT_ETAG_MISMATCH";
        }
        if (cause instanceof co.uk.wolfnotsheep.extraction.tika.parse.UnparseableDocumentException) {
            return "EXTRACTION_CORRUPT";
        }
        if (cause instanceof DocumentTooLargeException) {
            return "EXTRACTION_TOO_LARGE";
        }
        if (cause instanceof IdempotencyInFlightException) {
            return "IDEMPOTENCY_IN_FLIGHT";
        }
        if (cause instanceof UncheckedIOException) {
            return "EXTRACTION_SOURCE_UNAVAILABLE";
        }
        return "EXTRACTION_UNEXPECTED";
    }

    private void emitCompleted(ExtractRequest request, String traceparent, ExtractedText extracted, long durationMs) {
        AuditEmitter emitter = auditEmitterProvider.getIfAvailable();
        if (emitter == null) {
            return;
        }
        try {
            emitter.emit(ExtractionEvents.completed(
                    serviceName, serviceVersion, instanceId,
                    request.getNodeRunId(), traceparent,
                    extracted.detectedMimeType(), extracted.pageCount(),
                    extracted.byteCount(), durationMs, extracted.truncated()));
        } catch (RuntimeException e) {
            // Audit emission must never sink the response. The outbox
            // is durable; if the write itself fails the cause is the
            // emitter / Mongo, and the user-facing extract result is
            // still correct.
            log.warn("audit emit (EXTRACTION_COMPLETED) failed for nodeRunId={}: {}",
                    request.getNodeRunId(), e.getMessage());
        }
    }

    // ---- helpers -----------------------------------------------------

    private static DocumentRef toInternalRef(ExtractRequest request) {
        var generated = request.getDocumentRef();
        String etag = generated.getEtag();
        return etag == null || etag.isBlank()
                ? DocumentRef.of(generated.getBucket(), generated.getObjectKey())
                : DocumentRef.withEtag(generated.getBucket(), generated.getObjectKey(), etag);
    }

    private static ExtractResponse buildInlineResponse(
            ExtractRequest request, ExtractedText extracted, long durationMs) {
        ExtractResponseTextOneOf inline = new ExtractResponseTextOneOf();
        inline.setText(extracted.text());
        inline.setEncoding(ExtractResponseTextOneOf.EncodingEnum.UTF_8);
        return commonResponse(request, extracted, durationMs, inline);
    }

    private ExtractResponse buildRefResponse(
            ExtractRequest request, ExtractedText extracted, long durationMs) {
        ExtractedTextRef ref = sink.upload(request.getNodeRunId(), extracted.text());
        ExtractResponseTextOneOf1 byRef = new ExtractResponseTextOneOf1();
        byRef.setTextRef(ref.uri());
        byRef.setContentLength(ref.contentLength());
        byRef.setContentType(ref.contentType());
        return commonResponse(request, extracted, durationMs, byRef);
    }

    private static ExtractResponse commonResponse(
            ExtractRequest request, ExtractedText extracted, long durationMs, ExtractResponseText text) {
        ExtractResponse response = new ExtractResponse();
        response.setNodeRunId(request.getNodeRunId());
        response.setText(text);
        response.setDetectedMimeType(extracted.detectedMimeType());
        response.setPageCount(extracted.pageCount());
        response.setDurationMs((int) Math.min(Integer.MAX_VALUE, durationMs));
        response.setCostUnits((int) Math.min(Integer.MAX_VALUE,
                Math.max(0L, extracted.byteCount() / BYTES_PER_COST_UNIT)));
        return response;
    }
}
