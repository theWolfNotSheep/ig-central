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
import co.uk.wolfnotsheep.extraction.tika.source.DocumentRef;
import co.uk.wolfnotsheep.extraction.tika.source.DocumentSource;
import co.uk.wolfnotsheep.platformaudit.emit.AuditEmitter;
import co.uk.wolfnotsheep.platformaudit.envelope.AuditEvent;
import co.uk.wolfnotsheep.platformaudit.envelope.Outcome;
import co.uk.wolfnotsheep.platformaudit.envelope.Tier;
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
    private AuditEmitter auditEmitter;
    private ExtractController controller;

    @BeforeEach
    void setUp() {
        source = mock(DocumentSource.class);
        auditEmitter = mock(AuditEmitter.class);
        controller = new ExtractController(
                source,
                new TikaExtractionService(),
                providerOf(auditEmitter),
                /* inlineByteCeiling */ 262_144L,
                "gls-extraction-tika",
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
    void document_not_found_propagates() {
        when(source.open(any(DocumentRef.class)))
                .thenThrow(new DocumentNotFoundException(DocumentRef.of("b", "missing")));

        assertThatThrownBy(() ->
                controller.extractDocument(validTraceparent(), request("b", "missing", "n"), null))
                .isInstanceOf(DocumentNotFoundException.class);
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
    void payload_above_ceiling_raises_too_large() {
        // Construct a controller with a tiny ceiling so any non-empty text
        // overflows. Source returns enough bytes to extract past the limit.
        ExtractController tinyController = new ExtractController(
                source, new TikaExtractionService(),
                providerOf(auditEmitter),
                /* inlineByteCeiling */ 4L,
                "gls-extraction-tika", "0.0.1-SNAPSHOT", "test-instance");
        InputStream payload = new ByteArrayInputStream(
                "way more than four bytes".getBytes(StandardCharsets.UTF_8));
        when(source.open(any(DocumentRef.class))).thenReturn(payload);

        assertThatThrownBy(() ->
                tinyController.extractDocument(validTraceparent(),
                        request("b", "k", "n"), null))
                .isInstanceOf(DocumentTooLargeException.class);
        // No audit event for failure paths in this PR — handler-side
        // emission is a follow-up.
        verify(auditEmitter, never()).emit(any());
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
        assertThat(emitted.actor().service()).isEqualTo("gls-extraction-tika");
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
