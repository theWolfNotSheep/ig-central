package co.uk.wolfnotsheep.extraction.tika.web;

import co.uk.wolfnotsheep.extraction.tika.api.ExtractionApi;
import co.uk.wolfnotsheep.extraction.tika.audit.ExtractionEvents;
import co.uk.wolfnotsheep.extraction.tika.model.ExtractRequest;
import co.uk.wolfnotsheep.extraction.tika.model.ExtractResponse;
import co.uk.wolfnotsheep.extraction.tika.model.ExtractResponseText;
import co.uk.wolfnotsheep.extraction.tika.model.ExtractResponseTextOneOf;
import co.uk.wolfnotsheep.extraction.tika.parse.ExtractedText;
import co.uk.wolfnotsheep.extraction.tika.parse.TikaExtractionService;
import co.uk.wolfnotsheep.extraction.tika.source.DocumentRef;
import co.uk.wolfnotsheep.extraction.tika.source.DocumentSource;
import co.uk.wolfnotsheep.platformaudit.emit.AuditEmitter;
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
 * <p>Pipeline: source.open → tika.extract → response shape. Inline-only
 * for now — payloads above the configured byte ceiling raise
 * {@link DocumentTooLargeException} (mapped to 413 by the exception
 * handler). The MinIO sink for the {@code textRef} path lands in a
 * follow-up PR.
 *
 * <p>{@code nodeRunId} idempotency (CSV #16) is also a follow-up; until
 * then the {@code Idempotency-Key} header is logged but not consulted.
 */
@RestController
public class ExtractController implements ExtractionApi {

    private static final Logger log = LoggerFactory.getLogger(ExtractController.class);
    private static final int BYTES_PER_COST_UNIT = 1024;

    private final DocumentSource source;
    private final TikaExtractionService tika;
    private final ObjectProvider<AuditEmitter> auditEmitterProvider;
    private final long inlineByteCeiling;
    private final String serviceName;
    private final String serviceVersion;
    private final String instanceId;

    public ExtractController(
            DocumentSource source,
            TikaExtractionService tika,
            ObjectProvider<AuditEmitter> auditEmitterProvider,
            @Value("${gls.extraction.tika.inline-byte-ceiling:262144}") long inlineByteCeiling,
            @Value("${spring.application.name:gls-extraction-tika}") String serviceName,
            @Value("${gls.extraction.build.version:0.0.1-SNAPSHOT}") String serviceVersion,
            @Value("${HOSTNAME:unknown}") String instanceId) {
        this.source = source;
        this.tika = tika;
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

        DocumentRef ref = toInternalRef(request);
        Instant started = Instant.now();
        if (idempotencyKey != null) {
            log.debug("extract: nodeRunId={} idempotencyKey={} (cache check is a TODO)",
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
        if (textBytes > inlineByteCeiling) {
            // Sink lands in a follow-up; for now we surface this honestly
            // rather than silently overflowing into an unbounded inline
            // payload. See PR description for the textRef plan.
            throw new DocumentTooLargeException(
                    "extracted text is " + textBytes
                            + " bytes; inline ceiling is " + inlineByteCeiling
                            + ". MinIO sink (textRef) lands in a follow-up PR.");
        }

        long durationMs = Duration.between(started, Instant.now()).toMillis();
        emitCompleted(request, traceparent, extracted, durationMs);
        ExtractResponse response = buildInlineResponse(request, extracted, durationMs);
        return ResponseEntity.ok(response);
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

        ExtractResponse response = new ExtractResponse();
        response.setNodeRunId(request.getNodeRunId());
        response.setText((ExtractResponseText) inline);
        response.setDetectedMimeType(extracted.detectedMimeType());
        response.setPageCount(extracted.pageCount());
        response.setDurationMs((int) Math.min(Integer.MAX_VALUE, durationMs));
        response.setCostUnits((int) Math.min(Integer.MAX_VALUE,
                Math.max(0L, extracted.byteCount() / BYTES_PER_COST_UNIT)));
        return response;
    }
}
