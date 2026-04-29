package co.uk.wolfnotsheep.extraction.archive.web;

import co.uk.wolfnotsheep.extraction.archive.api.ExtractionApi;
import co.uk.wolfnotsheep.extraction.archive.audit.ArchiveEvents;
import co.uk.wolfnotsheep.extraction.archive.idempotency.IdempotencyInFlightException;
import co.uk.wolfnotsheep.extraction.archive.idempotency.IdempotencyOutcome;
import co.uk.wolfnotsheep.extraction.archive.idempotency.IdempotencyStore;
import co.uk.wolfnotsheep.extraction.archive.model.ChildDocument;
import co.uk.wolfnotsheep.extraction.archive.model.ChildDocumentDocumentRef;
import co.uk.wolfnotsheep.extraction.archive.model.ExtractRequest;
import co.uk.wolfnotsheep.extraction.archive.model.ExtractResponse;
import co.uk.wolfnotsheep.extraction.archive.parse.ArchiveCapsExceededException;
import co.uk.wolfnotsheep.extraction.archive.parse.ArchiveType;
import co.uk.wolfnotsheep.extraction.archive.parse.ArchiveWalkerDispatcher;
import co.uk.wolfnotsheep.extraction.archive.parse.ChildEmitter;
import co.uk.wolfnotsheep.extraction.archive.sink.ChildRef;
import co.uk.wolfnotsheep.extraction.archive.sink.ChildSink;
import co.uk.wolfnotsheep.extraction.archive.source.DocumentEtagMismatchException;
import co.uk.wolfnotsheep.extraction.archive.source.DocumentNotFoundException;
import co.uk.wolfnotsheep.extraction.archive.source.DocumentRef;
import co.uk.wolfnotsheep.extraction.archive.source.DocumentSource;
import co.uk.wolfnotsheep.platformaudit.emit.AuditEmitter;
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
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Implements the generated {@link ExtractionApi} interface from
 * {@code contracts/extraction-archive/openapi.yaml}.
 *
 * <p>Pipeline: {@code idempotency.tryAcquire} → {@code source.open} →
 * {@code dispatcher.dispatch} (which detects mime + selects walker
 * + drives a single-pass walk, calling our emitter for each direct
 * child) → {@code sink.upload} per child → assemble {@link ExtractResponse}
 * → cache + audit.
 *
 * <p>Caps are enforced inline by the emitter: total source bytes,
 * max child count, max single-child bytes. A breach throws
 * {@link ArchiveCapsExceededException} which the walker propagates
 * unchanged, the controller maps to a 413 + the matching
 * {@code ARCHIVE_*} {@code code} extension via the exception handler.
 *
 * <p>Recursion: this service does ONE LEVEL per invocation. Children
 * that are themselves archives get a {@code detectedMimeType} of e.g.
 * {@code application/zip}; the orchestrator routes each child back
 * through this service on a fresh {@code nodeRunId}.
 */
@RestController
public class ExtractController implements ExtractionApi {

    private static final Logger log = LoggerFactory.getLogger(ExtractController.class);
    private static final int BYTES_PER_COST_UNIT = 1024;

    private final DocumentSource source;
    private final ArchiveWalkerDispatcher dispatcher;
    private final ChildSink sink;
    private final IdempotencyStore idempotency;
    private final ExtractMetrics metrics;
    private final ObjectMapper mapper;
    private final ObjectProvider<AuditEmitter> auditEmitterProvider;
    private final long maxArchiveBytes;
    private final int maxChildren;
    private final long maxChildBytes;
    private final String serviceName;
    private final String serviceVersion;
    private final String instanceId;

    public ExtractController(
            DocumentSource source,
            ArchiveWalkerDispatcher dispatcher,
            ChildSink sink,
            IdempotencyStore idempotency,
            ExtractMetrics metrics,
            ObjectMapper mapper,
            ObjectProvider<AuditEmitter> auditEmitterProvider,
            @Value("${gls.extraction.archive.caps.max-archive-bytes:1073741824}") long maxArchiveBytes,
            @Value("${gls.extraction.archive.caps.max-children:5000}") int maxChildren,
            @Value("${gls.extraction.archive.caps.max-child-bytes:268435456}") long maxChildBytes,
            @Value("${spring.application.name:gls-extraction-archive}") String serviceName,
            @Value("${gls.extraction.archive.build.version:0.0.1-SNAPSHOT}") String serviceVersion,
            @Value("${HOSTNAME:unknown}") String instanceId) {
        this.source = source;
        this.dispatcher = dispatcher;
        this.sink = sink;
        this.idempotency = idempotency;
        this.metrics = metrics;
        this.mapper = mapper;
        this.auditEmitterProvider = auditEmitterProvider;
        this.maxArchiveBytes = maxArchiveBytes;
        this.maxChildren = maxChildren;
        this.maxChildBytes = maxChildBytes;
        this.serviceName = serviceName;
        this.serviceVersion = serviceVersion;
        this.instanceId = instanceId;
    }

    @Override
    public ResponseEntity<ExtractResponse> extractArchive(
            String traceparent,
            ExtractRequest request,
            String idempotencyKey) {

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
                    body == null || body.getArchiveType() == null ? null : body.getArchiveType().name(),
                    body == null || body.getChildCount() == null ? 0 : body.getChildCount(),
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
            String traceparent,
            ExtractRequest request,
            String idempotencyKey) {

        DocumentRef ref = toInternalRef(request);
        Instant started = Instant.now();
        if (idempotencyKey != null) {
            log.debug("extract: nodeRunId={} idempotencyKey={}",
                    request.getNodeRunId(), idempotencyKey);
        }

        // Pre-flight: reject archives over the byte cap before any
        // streaming. sizeOf() returns -1 if unknown (e.g. source
        // doesn't expose a HEAD); in that case the per-byte counter
        // inside the emitter takes over and trips on actual reads.
        long sourceSize = source.sizeOf(ref);
        if (sourceSize > maxArchiveBytes) {
            throw new ArchiveCapsExceededException(
                    ArchiveCapsExceededException.Cap.ARCHIVE_TOO_LARGE,
                    "source archive size " + sourceSize + " exceeds cap " + maxArchiveBytes);
        }

        List<ChildDocument> children = new ArrayList<>();
        long[] totalChildBytes = {0L};
        ArchiveWalkerDispatcher.DispatchResult dispatch;
        long byteCount;

        try (InputStream stream = source.open(ref);
             CountingInputStream counting = new CountingInputStream(stream, maxArchiveBytes)) {
            ChildEmitter emitter = (fileName, archivePath, contentTypeHint, size, content) -> {
                if (children.size() >= maxChildren) {
                    throw new ArchiveCapsExceededException(
                            ArchiveCapsExceededException.Cap.ARCHIVE_TOO_MANY_CHILDREN,
                            "child count exceeds cap " + maxChildren);
                }
                if (size > maxChildBytes) {
                    throw new ArchiveCapsExceededException(
                            ArchiveCapsExceededException.Cap.ARCHIVE_CHILD_TOO_LARGE,
                            "child '" + fileName + "' size " + size + " exceeds cap " + maxChildBytes);
                }
                int index = children.size();
                ChildBoundedInputStream bounded = new ChildBoundedInputStream(content, maxChildBytes, fileName);
                ChildRef stored = sink.upload(
                        request.getNodeRunId(), index, fileName, size,
                        contentTypeHint, bounded);
                totalChildBytes[0] += bounded.bytesRead();
                children.add(buildChild(fileName, archivePath, contentTypeHint,
                        bounded.bytesRead() >= 0 ? bounded.bytesRead() : size, stored));
            };
            dispatch = dispatcher.dispatch(counting, sourceFileName(request), emitter);
            byteCount = counting.bytesRead();
        } catch (UncheckedIOException ioWrap) {
            throw ioWrap;
        } catch (RuntimeException rethrow) {
            throw rethrow;
        } catch (Exception e) {
            throw new UncheckedIOException("Failed to close source stream", new IOException(e));
        }

        long durationMs = Duration.between(started, Instant.now()).toMillis();
        emitCompleted(request, traceparent, dispatch, children.size(), byteCount, durationMs);

        ExtractResponse response = buildResponse(request, dispatch, children, byteCount, durationMs);
        return ResponseEntity.ok(response);
    }

    private ExtractResponse buildResponse(
            ExtractRequest request,
            ArchiveWalkerDispatcher.DispatchResult dispatch,
            List<ChildDocument> children,
            long byteCount,
            long durationMs) {
        ExtractResponse response = new ExtractResponse();
        response.setNodeRunId(request.getNodeRunId());
        response.setDetectedMimeType(dispatch.detectedMimeType());
        response.setArchiveType(toArchiveTypeEnum(dispatch.type()));
        response.setChildCount(children.size());
        response.setChildren(children);
        response.setDurationMs((int) Math.min(Integer.MAX_VALUE, durationMs));
        response.setCostUnits((int) Math.min(Integer.MAX_VALUE, Math.max(0L, byteCount / BYTES_PER_COST_UNIT)));
        return response;
    }

    private static ChildDocument buildChild(
            String fileName,
            String archivePath,
            String contentTypeHint,
            long size,
            ChildRef stored) {
        ChildDocumentDocumentRef ref = new ChildDocumentDocumentRef();
        ref.setBucket(stored.bucket());
        ref.setObjectKey(stored.objectKey());
        ref.setEtag(stored.etag());
        ChildDocument child = new ChildDocument();
        child.setDocumentRef(ref);
        child.setFileName(fileName == null || fileName.isBlank() ? "child" : fileName);
        child.setSize(Math.max(0L, size));
        child.setDetectedMimeType(contentTypeHint == null || contentTypeHint.isBlank()
                ? "application/octet-stream" : contentTypeHint);
        child.setArchivePath(archivePath);
        return child;
    }

    private static ExtractResponse.ArchiveTypeEnum toArchiveTypeEnum(ArchiveType type) {
        return switch (type) {
            case ZIP -> ExtractResponse.ArchiveTypeEnum.ZIP;
            case MBOX -> ExtractResponse.ArchiveTypeEnum.MBOX;
            case PST -> ExtractResponse.ArchiveTypeEnum.PST;
        };
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
        if (response == null) {
            return;
        }
        try {
            String json = mapper.writeValueAsString(response);
            idempotency.cacheResult(nodeRunId, json);
        } catch (JsonProcessingException e) {
            log.warn("idempotency cache write failed for nodeRunId={}: {}",
                    nodeRunId, e.getMessage());
        }
    }

    private void emitCompleted(
            ExtractRequest request,
            String traceparent,
            ArchiveWalkerDispatcher.DispatchResult dispatch,
            int childCount,
            long byteCount,
            long durationMs) {
        AuditEmitter emitter = auditEmitterProvider.getIfAvailable();
        if (emitter == null) {
            return;
        }
        try {
            emitter.emit(ArchiveEvents.completed(
                    serviceName, serviceVersion, instanceId,
                    request.getNodeRunId(), traceparent,
                    dispatch.type().name().toLowerCase(),
                    dispatch.detectedMimeType(),
                    childCount, byteCount, durationMs));
        } catch (RuntimeException e) {
            log.warn("audit emit (EXTRACTION_COMPLETED) failed for nodeRunId={}: {}",
                    request.getNodeRunId(), e.getMessage());
        }
    }

    private void emitFailed(ExtractRequest request, String traceparent, Throwable cause) {
        AuditEmitter emitter = auditEmitterProvider.getIfAvailable();
        if (emitter == null) {
            return;
        }
        String errorCode = errorCodeFor(cause);
        String nodeRunId = request != null ? request.getNodeRunId() : null;
        try {
            emitter.emit(ArchiveEvents.failed(
                    serviceName, serviceVersion, instanceId,
                    nodeRunId, traceparent,
                    errorCode, cause.getMessage()));
        } catch (RuntimeException e) {
            log.warn("audit emit (EXTRACTION_FAILED) failed for nodeRunId={}: {}",
                    nodeRunId, e.getMessage());
        }
    }

    private static String errorCodeFor(Throwable cause) {
        if (cause instanceof DocumentNotFoundException) return "DOCUMENT_NOT_FOUND";
        if (cause instanceof DocumentEtagMismatchException) return "DOCUMENT_ETAG_MISMATCH";
        if (cause instanceof co.uk.wolfnotsheep.extraction.archive.parse.CorruptArchiveException) return "ARCHIVE_CORRUPT";
        if (cause instanceof co.uk.wolfnotsheep.extraction.archive.parse.UnsupportedArchiveTypeException) return "ARCHIVE_UNSUPPORTED_TYPE";
        if (cause instanceof ArchiveCapsExceededException caps) return caps.cap().name();
        if (cause instanceof IdempotencyInFlightException) return "IDEMPOTENCY_IN_FLIGHT";
        if (cause instanceof UncheckedIOException) return "ARCHIVE_SOURCE_UNAVAILABLE";
        return "ARCHIVE_UNEXPECTED";
    }

    private static DocumentRef toInternalRef(ExtractRequest request) {
        var generated = request.getDocumentRef();
        String etag = generated.getEtag();
        return etag == null || etag.isBlank()
                ? DocumentRef.of(generated.getBucket(), generated.getObjectKey())
                : DocumentRef.withEtag(generated.getBucket(), generated.getObjectKey(), etag);
    }

    private static String sourceFileName(ExtractRequest request) {
        var ref = request.getDocumentRef();
        return ref == null ? null : ref.getObjectKey();
    }

    private static long approxByteCount(ExtractResponse body) {
        // Best-effort metric input — costUnits is ceil(byteCount / 1024).
        return body.getCostUnits() == null ? 0L : ((long) body.getCostUnits()) * BYTES_PER_COST_UNIT;
    }

    /**
     * Wraps the source archive stream and trips
     * {@link ArchiveCapsExceededException} the moment the byte counter
     * crosses {@code limit}. Belt + braces against archives whose
     * declared {@code Content-Length} disagrees with the actual stream
     * length.
     */
    static final class CountingInputStream extends InputStream {
        private final InputStream delegate;
        private final long limit;
        private long count;

        CountingInputStream(InputStream delegate, long limit) {
            this.delegate = delegate;
            this.limit = limit;
        }

        long bytesRead() {
            return count;
        }

        @Override
        public int read() throws IOException {
            int b = delegate.read();
            if (b >= 0) bumpAndCheck(1);
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int n = delegate.read(b, off, len);
            if (n > 0) bumpAndCheck(n);
            return n;
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }

        private void bumpAndCheck(int delta) {
            count += delta;
            if (count > limit) {
                throw new ArchiveCapsExceededException(
                        ArchiveCapsExceededException.Cap.ARCHIVE_TOO_LARGE,
                        "source archive size exceeds cap " + limit);
            }
        }
    }

    /**
     * Wraps a single child entry stream with a byte cap matching the
     * {@code maxChildBytes} configuration. Trips
     * {@link ArchiveCapsExceededException} as soon as a zip-bomb-style
     * inflation crosses the threshold — without this, decompression
     * could OOM the JVM before the controller's post-walk size check
     * runs.
     */
    static final class ChildBoundedInputStream extends InputStream {
        private final InputStream delegate;
        private final long limit;
        private final String fileName;
        private long count;

        ChildBoundedInputStream(InputStream delegate, long limit, String fileName) {
            this.delegate = delegate;
            this.limit = limit;
            this.fileName = fileName;
        }

        long bytesRead() {
            return count;
        }

        @Override
        public int read() throws IOException {
            int b = delegate.read();
            if (b >= 0) bumpAndCheck(1);
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int n = delegate.read(b, off, len);
            if (n > 0) bumpAndCheck(n);
            return n;
        }

        private void bumpAndCheck(int delta) {
            count += delta;
            if (count > limit) {
                throw new ArchiveCapsExceededException(
                        ArchiveCapsExceededException.Cap.ARCHIVE_CHILD_TOO_LARGE,
                        "child '" + fileName + "' uncompressed size exceeds cap " + limit);
            }
        }

        @Override
        public void close() throws IOException {
            // Don't close — owned by the walker.
        }
    }
}
