package co.uk.wolfnotsheep.extraction.tika.web;

import co.uk.wolfnotsheep.extraction.tika.api.ExtractionApi;
import co.uk.wolfnotsheep.extraction.tika.model.ExtractRequest;
import co.uk.wolfnotsheep.extraction.tika.model.ExtractRequestDocumentRef;
import co.uk.wolfnotsheep.extraction.tika.model.ExtractResponse;
import co.uk.wolfnotsheep.extraction.tika.model.ExtractResponseTextOneOf;
import co.uk.wolfnotsheep.extraction.tika.parse.ExtractedText;
import co.uk.wolfnotsheep.extraction.tika.parse.TikaExtractionService;
import co.uk.wolfnotsheep.extraction.tika.parse.UnparseableDocumentException;
import co.uk.wolfnotsheep.extraction.tika.source.DocumentEtagMismatchException;
import co.uk.wolfnotsheep.extraction.tika.source.DocumentNotFoundException;
import co.uk.wolfnotsheep.extraction.tika.idempotency.IdempotencyInFlightException;
import co.uk.wolfnotsheep.extraction.tika.idempotency.IdempotencyOutcome;
import co.uk.wolfnotsheep.extraction.tika.idempotency.IdempotencyStore;
import co.uk.wolfnotsheep.extraction.tika.sink.DocumentSink;
import co.uk.wolfnotsheep.extraction.tika.sink.ExtractedTextRef;
import co.uk.wolfnotsheep.extraction.tika.source.DocumentRef;
import co.uk.wolfnotsheep.extraction.tika.source.DocumentSource;
import co.uk.wolfnotsheep.platformaudit.emit.AuditEmitter;
import co.uk.wolfnotsheep.platformaudit.envelope.AuditEvent;
import co.uk.wolfnotsheep.platformaudit.envelope.Outcome;
import co.uk.wolfnotsheep.platformaudit.envelope.Tier;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Direct controller-level tests — no MockMvc, no Spring context.
 * Wires the controller against mock {@link DocumentSource} and a real
 * {@link TikaExtractionService} (Tika is the substrate we want to
 * exercise; the source is the boundary we want to mock).
 */
class ExtractControllerTest {

    private DocumentSource source;
    private DocumentSink sink;
    private IdempotencyStore idempotency;
    private ObjectMapper mapper;
    private AuditEmitter auditEmitter;
    private ExtractController controller;

    @BeforeEach
    void setUp() {
        source = mock(DocumentSource.class);
        sink = mock(DocumentSink.class);
        idempotency = mock(IdempotencyStore.class);
        when(idempotency.tryAcquire(any())).thenReturn(IdempotencyOutcome.acquired());
        mapper = new ObjectMapper();
        auditEmitter = mock(AuditEmitter.class);
        controller = new ExtractController(
                source,
                new TikaExtractionService(),
                sink,
                idempotency,
                new ExtractMetrics(new SimpleMeterRegistry()),
                mapper,
                providerOf(auditEmitter),
                /* inlineByteCeiling */ 262_144L,
                "igc-extraction-tika",
                "0.0.1-SNAPSHOT",
                "test-instance");
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<AuditEmitter> providerOf(AuditEmitter emitter) {
        ObjectProvider<AuditEmitter> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(emitter);
        return provider;
    }

    @Test
    void happy_path_returns_200_with_inline_text() {
        InputStream payload = new ByteArrayInputStream("hello world".getBytes(StandardCharsets.UTF_8));
        when(source.open(any(DocumentRef.class))).thenReturn(payload);

        ResponseEntity<ExtractResponse> resp = controller.extractDocument(
                validTraceparent(), request("bucket", "doc.txt", "node-1"), null);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        ExtractResponse body = resp.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getNodeRunId()).isEqualTo("node-1");
        assertThat(body.getText()).isInstanceOf(ExtractResponseTextOneOf.class);
        ExtractResponseTextOneOf inline = (ExtractResponseTextOneOf) body.getText();
        assertThat(inline.getText()).contains("hello world");
        assertThat(body.getDetectedMimeType()).startsWith("text/plain");
        assertThat(body.getCostUnits()).isZero(); // 11 bytes / 1024 = 0
        assertThat(body.getDurationMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void document_not_found_propagates_and_emits_FAILED_event() {
        when(source.open(any(DocumentRef.class)))
                .thenThrow(new DocumentNotFoundException(DocumentRef.of("b", "missing")));

        assertThatThrownBy(() ->
                controller.extractDocument(validTraceparent(), request("b", "missing", "node-nf"), null))
                .isInstanceOf(DocumentNotFoundException.class);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEmitter, times(1)).emit(captor.capture());
        assertThat(captor.getValue().details().metadata())
                .containsEntry("errorCode", "DOCUMENT_NOT_FOUND");
        assertThat(captor.getValue().nodeRunId()).isEqualTo("node-nf");
    }

    @Test
    void etag_mismatch_propagates() {
        when(source.open(any(DocumentRef.class)))
                .thenThrow(new DocumentEtagMismatchException(
                        DocumentRef.withEtag("b", "k", "expected"), "actual"));

        ExtractRequestDocumentRef ref = new ExtractRequestDocumentRef();
        ref.setBucket("b");
        ref.setObjectKey("k");
        ref.setEtag("expected");
        ExtractRequest req = new ExtractRequest();
        req.setDocumentRef(ref);
        req.setNodeRunId("node-1");

        assertThatThrownBy(() -> controller.extractDocument(validTraceparent(), req, null))
                .isInstanceOf(DocumentEtagMismatchException.class);
    }

    @Test
    void unparseable_document_propagates() {
        InputStream pseudoPdf = new ByteArrayInputStream(
                "%PDF-1.4\nthis is not a real pdf at all\n%%EOF".getBytes(StandardCharsets.UTF_8));
        when(source.open(any(DocumentRef.class))).thenReturn(pseudoPdf);

        assertThatThrownBy(() ->
                controller.extractDocument(validTraceparent(),
                        request("bucket", "fake.pdf", "node-1"), null))
                .isInstanceOf(UnparseableDocumentException.class);
    }

    @Test
    void payload_above_ceiling_uploads_via_sink_and_returns_textRef_branch() {
        // Construct a controller with a tiny ceiling so any non-empty text
        // overflows. Source returns enough bytes to extract past the limit.
        ExtractController tinyController = new ExtractController(
                source, new TikaExtractionService(), sink,
                idempotency, new ExtractMetrics(new SimpleMeterRegistry()), mapper,
                providerOf(auditEmitter),
                /* inlineByteCeiling */ 4L,
                "igc-extraction-tika", "0.0.1-SNAPSHOT", "test-instance");
        InputStream payload = new ByteArrayInputStream(
                "way more than four bytes".getBytes(StandardCharsets.UTF_8));
        when(source.open(any(DocumentRef.class))).thenReturn(payload);
        when(sink.upload(any(), any())).thenReturn(new ExtractedTextRef(
                java.net.URI.create("minio://igc-extracted-text/extracted/n.txt"),
                42L, "text/plain;charset=utf-8"));

        ResponseEntity<co.uk.wolfnotsheep.extraction.tika.model.ExtractResponse> resp =
                tinyController.extractDocument(validTraceparent(),
                        request("b", "k", "n"), null);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        var body = resp.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getText())
                .isInstanceOf(co.uk.wolfnotsheep.extraction.tika.model.ExtractResponseTextOneOf1.class);
        var byRef = (co.uk.wolfnotsheep.extraction.tika.model.ExtractResponseTextOneOf1) body.getText();
        assertThat(byRef.getTextRef().toString()).isEqualTo("minio://igc-extracted-text/extracted/n.txt");
        assertThat(byRef.getContentLength()).isEqualTo(42L);

        // Sink-path is a SUCCESS — emits EXTRACTION_COMPLETED, not FAILED.
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEmitter, times(1)).emit(captor.capture());
        assertThat(captor.getValue().eventType()).isEqualTo("EXTRACTION_COMPLETED");
        assertThat(captor.getValue().outcome()).isEqualTo(Outcome.SUCCESS);
        verify(sink, times(1)).upload(eq("n"), any());
    }

    @Test
    void inline_path_does_not_call_the_sink() {
        InputStream payload = new ByteArrayInputStream("small".getBytes(StandardCharsets.UTF_8));
        when(source.open(any(DocumentRef.class))).thenReturn(payload);

        controller.extractDocument(validTraceparent(),
                request("bucket", "doc.txt", "node-inline"), null);

        verify(sink, never()).upload(any(), any());
    }

    @Test
    void successful_extract_emits_EXTRACTION_COMPLETED_tier_2() {
        InputStream payload = new ByteArrayInputStream(
                "audited content".getBytes(StandardCharsets.UTF_8));
        when(source.open(any(DocumentRef.class))).thenReturn(payload);

        controller.extractDocument(validTraceparent(),
                request("bucket", "doc.txt", "node-audit-1"), null);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEmitter, times(1)).emit(captor.capture());
        AuditEvent emitted = captor.getValue();
        assertThat(emitted.eventType()).isEqualTo("EXTRACTION_COMPLETED");
        assertThat(emitted.tier()).isEqualTo(Tier.SYSTEM);
        assertThat(emitted.outcome()).isEqualTo(Outcome.SUCCESS);
        assertThat(emitted.action()).isEqualTo("EXTRACT");
        assertThat(emitted.nodeRunId()).isEqualTo("node-audit-1");
        assertThat(emitted.traceparent()).isEqualTo(validTraceparent());
        assertThat(emitted.actor().service()).isEqualTo("igc-extraction-tika");
        assertThat(emitted.details().metadata()).containsEntry("nodeRunId", "node-audit-1");
        assertThat(emitted.details().metadata()).containsKey("byteCount");
    }

    @Test
    void audit_emit_failure_does_not_sink_the_response() {
        InputStream payload = new ByteArrayInputStream(
                "still works".getBytes(StandardCharsets.UTF_8));
        when(source.open(any(DocumentRef.class))).thenReturn(payload);
        org.mockito.Mockito.doThrow(new RuntimeException("audit pipe down"))
                .when(auditEmitter).emit(any());

        ResponseEntity<ExtractResponse> resp = controller.extractDocument(
                validTraceparent(), request("b", "k", "node-x"), null);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void controller_implements_generated_api() {
        // Caught at compile time by the implements clause; this assertion
        // is a documentation-style guard against accidentally dropping it.
        assertThat(ExtractionApi.class.isAssignableFrom(ExtractController.class)).isTrue();
    }

    @Test
    void in_flight_idempotency_returns_409_via_exception() {
        when(idempotency.tryAcquire("node-busy")).thenReturn(IdempotencyOutcome.inFlight());

        assertThatThrownBy(() ->
                controller.extractDocument(validTraceparent(),
                        request("b", "k", "node-busy"), null))
                .isInstanceOf(IdempotencyInFlightException.class);

        // No source / sink / Tika activity on the in-flight short-circuit.
        verify(source, never()).open(any());
        verify(sink, never()).upload(any(), any());
    }

    @Test
    void cached_idempotency_returns_stored_response_without_extracting() throws Exception {
        ExtractResponse cached = new ExtractResponse();
        cached.setNodeRunId("node-c");
        ExtractResponseTextOneOf inline = new ExtractResponseTextOneOf();
        inline.setText("from cache");
        inline.setEncoding(ExtractResponseTextOneOf.EncodingEnum.UTF_8);
        cached.setText(inline);
        cached.setDetectedMimeType("text/plain");
        cached.setDurationMs(0);
        cached.setCostUnits(0);
        String json = mapper.writeValueAsString(cached);
        when(idempotency.tryAcquire("node-c")).thenReturn(IdempotencyOutcome.cached(json));

        ResponseEntity<ExtractResponse> resp = controller.extractDocument(
                validTraceparent(), request("b", "k", "node-c"), null);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getNodeRunId()).isEqualTo("node-c");
        verify(source, never()).open(any());
        verify(auditEmitter, never()).emit(any());
    }

    @Test
    void successful_extract_caches_the_response_for_subsequent_retries() {
        InputStream payload = new ByteArrayInputStream("ok".getBytes(StandardCharsets.UTF_8));
        when(source.open(any(DocumentRef.class))).thenReturn(payload);

        controller.extractDocument(validTraceparent(),
                request("b", "k", "node-cache"), null);

        verify(idempotency, times(1)).cacheResult(eq("node-cache"), any());
    }

    @Test
    void failure_releases_the_idempotency_row_so_retries_can_proceed() {
        when(source.open(any(DocumentRef.class)))
                .thenThrow(new DocumentNotFoundException(DocumentRef.of("b", "missing")));

        assertThatThrownBy(() ->
                controller.extractDocument(validTraceparent(),
                        request("b", "missing", "node-fail"), null))
                .isInstanceOf(DocumentNotFoundException.class);

        verify(idempotency, times(1)).releaseOnFailure("node-fail");
        verify(idempotency, never()).cacheResult(any(), any());
    }

    private static ExtractRequest request(String bucket, String objectKey, String nodeRunId) {
        ExtractRequestDocumentRef ref = new ExtractRequestDocumentRef();
        ref.setBucket(bucket);
        ref.setObjectKey(objectKey);
        ExtractRequest req = new ExtractRequest();
        req.setDocumentRef(ref);
        req.setNodeRunId(nodeRunId);
        return req;
    }

    private static String validTraceparent() {
        return "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01";
    }
}
