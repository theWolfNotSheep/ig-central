package co.uk.wolfnotsheep.extraction.ocr.web;

import co.uk.wolfnotsheep.extraction.ocr.api.ExtractionApi;
import co.uk.wolfnotsheep.extraction.ocr.idempotency.IdempotencyInFlightException;
import co.uk.wolfnotsheep.extraction.ocr.idempotency.IdempotencyOutcome;
import co.uk.wolfnotsheep.extraction.ocr.idempotency.IdempotencyStore;
import co.uk.wolfnotsheep.extraction.ocr.model.ExtractRequest;
import co.uk.wolfnotsheep.extraction.ocr.model.ExtractRequestDocumentRef;
import co.uk.wolfnotsheep.extraction.ocr.model.ExtractResponse;
import co.uk.wolfnotsheep.extraction.ocr.model.ExtractResponseTextOneOf;
import co.uk.wolfnotsheep.extraction.ocr.parse.OcrExtractionService;
import co.uk.wolfnotsheep.extraction.ocr.parse.OcrLanguageUnsupportedException;
import co.uk.wolfnotsheep.extraction.ocr.parse.OcrResult;
import co.uk.wolfnotsheep.extraction.ocr.parse.UnparseableImageException;
import co.uk.wolfnotsheep.extraction.ocr.sink.DocumentSink;
import co.uk.wolfnotsheep.extraction.ocr.sink.ExtractedTextRef;
import co.uk.wolfnotsheep.extraction.ocr.source.DocumentNotFoundException;
import co.uk.wolfnotsheep.extraction.ocr.source.DocumentRef;
import co.uk.wolfnotsheep.extraction.ocr.source.DocumentSource;
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
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

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
 * Direct controller-level tests with a fake {@link OcrExtractionService} —
 * keeps the test off the native Tesseract binary, which isn't on the
 * test host's classpath. Real-engine coverage lands as integration
 * tests when issue #7 unblocks Testcontainers.
 */
class ExtractControllerTest {

    private DocumentSource source;
    private OcrExtractionService ocr;
    private DocumentSink sink;
    private IdempotencyStore idempotency;
    private ObjectMapper mapper;
    private AuditEmitter auditEmitter;
    private ExtractController controller;

    @BeforeEach
    void setUp() {
        source = mock(DocumentSource.class);
        ocr = mock(OcrExtractionService.class);
        sink = mock(DocumentSink.class);
        idempotency = mock(IdempotencyStore.class);
        when(idempotency.tryAcquire(any())).thenReturn(IdempotencyOutcome.acquired());
        when(source.sizeOf(any())).thenReturn(-1L);
        mapper = new ObjectMapper();
        auditEmitter = mock(AuditEmitter.class);
        controller = new ExtractController(
                source, ocr, sink, idempotency,
                new ExtractMetrics(new SimpleMeterRegistry()), mapper,
                providerOf(auditEmitter),
                /* inlineByteCeiling */ 262_144L,
                /* maxSourceBytes */ 268_435_456L,
                "gls-extraction-ocr", "0.0.1-SNAPSHOT", "test-instance");
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<AuditEmitter> providerOf(AuditEmitter emitter) {
        ObjectProvider<AuditEmitter> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(emitter);
        return provider;
    }

    @Test
    void happy_path_returns_200_with_inline_text() {
        when(source.open(any())).thenReturn(new ByteArrayInputStream("img".getBytes(StandardCharsets.UTF_8)));
        when(ocr.run(any(InputStream.class), any(), any()))
                .thenReturn(new OcrResult("hello world", "image/png", List.of("eng"), null, 1024L));

        ResponseEntity<ExtractResponse> resp = controller.extractOcr(
                validTraceparent(), request("bucket", "doc.png", "node-1"), null);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        ExtractResponse body = resp.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getNodeRunId()).isEqualTo("node-1");
        assertThat(body.getDetectedMimeType()).isEqualTo("image/png");
        assertThat(body.getLanguages()).containsExactly("eng");
        assertThat(body.getText()).isInstanceOf(ExtractResponseTextOneOf.class);
        ExtractResponseTextOneOf inline = (ExtractResponseTextOneOf) body.getText();
        assertThat(inline.getText()).isEqualTo("hello world");
        assertThat(body.getCostUnits()).isEqualTo(1);
    }

    @Test
    void large_text_uploads_via_sink_and_returns_textRef_branch() {
        when(source.open(any())).thenReturn(new ByteArrayInputStream("img".getBytes(StandardCharsets.UTF_8)));
        String huge = "a".repeat(300_000);
        when(ocr.run(any(InputStream.class), any(), any()))
                .thenReturn(new OcrResult(huge, "application/pdf", List.of("eng"), 5, 1_000_000L));
        when(sink.upload(eq("node-large"), eq(huge)))
                .thenReturn(new ExtractedTextRef(URI.create("minio://gls-ocr-text/ocr/node-large.txt"),
                        300_000L, "text/plain;charset=utf-8"));

        ResponseEntity<ExtractResponse> resp = controller.extractOcr(
                validTraceparent(), request("b", "k", "node-large"), null);

        var body = resp.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getText())
                .isInstanceOf(co.uk.wolfnotsheep.extraction.ocr.model.ExtractResponseTextOneOf1.class);
        assertThat(body.getPageCount()).isEqualTo(5);
        verify(sink, times(1)).upload(eq("node-large"), any());
    }

    @Test
    void document_not_found_propagates_and_emits_FAILED_event() {
        when(source.open(any())).thenThrow(new DocumentNotFoundException(DocumentRef.of("b", "x")));

        assertThatThrownBy(() -> controller.extractOcr(validTraceparent(),
                request("b", "x", "node-nf"), null))
                .isInstanceOf(DocumentNotFoundException.class);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEmitter, times(1)).emit(captor.capture());
        assertThat(captor.getValue().details().metadata())
                .containsEntry("errorCode", "DOCUMENT_NOT_FOUND");
        assertThat(captor.getValue().outcome()).isEqualTo(Outcome.FAILURE);
    }

    @Test
    void unparseable_image_propagates_as_OCR_CORRUPT() {
        when(source.open(any())).thenReturn(new ByteArrayInputStream("img".getBytes(StandardCharsets.UTF_8)));
        when(ocr.run(any(InputStream.class), any(), any()))
                .thenThrow(new UnparseableImageException("Tesseract got nothing", new RuntimeException()));

        assertThatThrownBy(() -> controller.extractOcr(validTraceparent(),
                request("b", "k", "node-corrupt"), null))
                .isInstanceOf(UnparseableImageException.class);
    }

    @Test
    void unsupported_language_propagates_as_OCR_LANGUAGE_UNSUPPORTED() {
        when(source.open(any())).thenReturn(new ByteArrayInputStream("img".getBytes(StandardCharsets.UTF_8)));
        when(ocr.run(any(InputStream.class), any(), any()))
                .thenThrow(new OcrLanguageUnsupportedException("zzz not installed"));

        assertThatThrownBy(() -> controller.extractOcr(validTraceparent(),
                requestWithLanguages("b", "k", "node-lang", List.of("zzz")), null))
                .isInstanceOf(OcrLanguageUnsupportedException.class);
    }

    @Test
    void source_too_large_throws_DocumentTooLarge() {
        when(source.sizeOf(any())).thenReturn(500_000_000L);

        assertThatThrownBy(() -> controller.extractOcr(validTraceparent(),
                request("b", "k", "node-big"), null))
                .isInstanceOf(DocumentTooLargeException.class);
    }

    @Test
    void successful_extract_emits_EXTRACTION_COMPLETED_tier_2() {
        when(source.open(any())).thenReturn(new ByteArrayInputStream("img".getBytes(StandardCharsets.UTF_8)));
        when(ocr.run(any(InputStream.class), any(), any()))
                .thenReturn(new OcrResult("ok", "image/png", List.of("eng"), null, 50L));

        controller.extractOcr(validTraceparent(),
                request("bucket", "p.png", "node-audit"), null);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEmitter, times(1)).emit(captor.capture());
        AuditEvent emitted = captor.getValue();
        assertThat(emitted.eventType()).isEqualTo("EXTRACTION_COMPLETED");
        assertThat(emitted.tier()).isEqualTo(Tier.SYSTEM);
        assertThat(emitted.outcome()).isEqualTo(Outcome.SUCCESS);
        assertThat(emitted.action()).isEqualTo("EXTRACT");
        assertThat(emitted.nodeRunId()).isEqualTo("node-audit");
        assertThat(emitted.details().metadata()).containsEntry("languages", List.of("eng"));
    }

    @Test
    void in_flight_idempotency_short_circuits_without_calling_ocr() {
        when(idempotency.tryAcquire("node-busy")).thenReturn(IdempotencyOutcome.inFlight());

        assertThatThrownBy(() -> controller.extractOcr(validTraceparent(),
                request("b", "k", "node-busy"), null))
                .isInstanceOf(IdempotencyInFlightException.class);

        verify(source, never()).open(any());
        verify(ocr, never()).run(any(InputStream.class), any(), any());
    }

    @Test
    void cached_idempotency_returns_stored_response() throws Exception {
        ExtractResponse cached = new ExtractResponse();
        cached.setNodeRunId("node-c");
        cached.setDetectedMimeType("image/png");
        ExtractResponseTextOneOf inline = new ExtractResponseTextOneOf();
        inline.setText("from cache");
        inline.setEncoding(ExtractResponseTextOneOf.EncodingEnum.UTF_8);
        cached.setText(inline);
        cached.setLanguages(List.of("eng"));
        cached.setDurationMs(0);
        cached.setCostUnits(0);
        String json = mapper.writeValueAsString(cached);
        when(idempotency.tryAcquire("node-c")).thenReturn(IdempotencyOutcome.cached(json));

        ResponseEntity<ExtractResponse> resp = controller.extractOcr(
                validTraceparent(), request("b", "k", "node-c"), null);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
        verify(source, never()).open(any());
        verify(auditEmitter, never()).emit(any());
    }

    @Test
    void controller_implements_generated_api() {
        assertThat(ExtractionApi.class.isAssignableFrom(ExtractController.class)).isTrue();
    }

    private static ExtractRequest request(String bucket, String objectKey, String nodeRunId) {
        return requestWithLanguages(bucket, objectKey, nodeRunId, null);
    }

    private static ExtractRequest requestWithLanguages(String bucket, String objectKey,
                                                       String nodeRunId, List<String> langs) {
        ExtractRequestDocumentRef ref = new ExtractRequestDocumentRef();
        ref.setBucket(bucket);
        ref.setObjectKey(objectKey);
        ExtractRequest req = new ExtractRequest();
        req.setDocumentRef(ref);
        req.setNodeRunId(nodeRunId);
        if (langs != null) req.setLanguages(langs);
        return req;
    }

    private static String validTraceparent() {
        return "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01";
    }
}
