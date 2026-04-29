package co.uk.wolfnotsheep.extraction.archive.web;

import co.uk.wolfnotsheep.extraction.archive.api.ExtractionApi;
import co.uk.wolfnotsheep.extraction.archive.idempotency.IdempotencyInFlightException;
import co.uk.wolfnotsheep.extraction.archive.idempotency.IdempotencyOutcome;
import co.uk.wolfnotsheep.extraction.archive.idempotency.IdempotencyStore;
import co.uk.wolfnotsheep.extraction.archive.model.ExtractRequest;
import co.uk.wolfnotsheep.extraction.archive.model.ExtractRequestDocumentRef;
import co.uk.wolfnotsheep.extraction.archive.model.ExtractResponse;
import co.uk.wolfnotsheep.extraction.archive.parse.ArchiveCapsExceededException;
import co.uk.wolfnotsheep.extraction.archive.parse.ArchiveWalkerDispatcher;
import co.uk.wolfnotsheep.extraction.archive.parse.MboxArchiveWalker;
import co.uk.wolfnotsheep.extraction.archive.parse.ZipArchiveWalker;
import co.uk.wolfnotsheep.extraction.archive.sink.ChildRef;
import co.uk.wolfnotsheep.extraction.archive.sink.ChildSink;
import co.uk.wolfnotsheep.extraction.archive.source.DocumentNotFoundException;
import co.uk.wolfnotsheep.extraction.archive.source.DocumentRef;
import co.uk.wolfnotsheep.extraction.archive.source.DocumentSource;
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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Direct controller-level tests — no MockMvc, no Spring context. Wires
 * the controller against a mocked {@link DocumentSource} +
 * {@link ChildSink} but uses the real {@link ArchiveWalkerDispatcher}
 * with the real ZIP / MBOX walkers (the parsing path is the substrate
 * we want exercised; the storage boundary is the one to mock).
 */
class ExtractControllerTest {

    private DocumentSource source;
    private ChildSink sink;
    private IdempotencyStore idempotency;
    private ObjectMapper mapper;
    private AuditEmitter auditEmitter;
    private ArchiveWalkerDispatcher dispatcher;
    private ExtractController controller;

    @BeforeEach
    void setUp() {
        source = mock(DocumentSource.class);
        sink = mock(ChildSink.class);
        idempotency = mock(IdempotencyStore.class);
        when(idempotency.tryAcquire(any())).thenReturn(IdempotencyOutcome.acquired());
        mapper = new ObjectMapper();
        auditEmitter = mock(AuditEmitter.class);
        dispatcher = new ArchiveWalkerDispatcher(List.of(
                new ZipArchiveWalker(), new MboxArchiveWalker()));
        controller = newController(/* maxArchiveBytes */ Long.MAX_VALUE,
                /* maxChildren */ 1000, /* maxChildBytes */ Long.MAX_VALUE);
    }

    private ExtractController newController(long maxArchiveBytes, int maxChildren, long maxChildBytes) {
        return new ExtractController(
                source, dispatcher, sink, idempotency,
                new ExtractMetrics(new SimpleMeterRegistry()),
                mapper,
                providerOf(auditEmitter),
                maxArchiveBytes, maxChildren, maxChildBytes,
                "gls-extraction-archive", "0.0.1-SNAPSHOT", "test-instance");
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<AuditEmitter> providerOf(AuditEmitter emitter) {
        ObjectProvider<AuditEmitter> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(emitter);
        return provider;
    }

    @Test
    void happy_path_unpacks_zip_and_returns_children_inline() throws IOException {
        byte[] zip = makeZip("a.txt", "alpha", "b.txt", "beta");
        when(source.open(any(DocumentRef.class))).thenReturn(new ByteArrayInputStream(zip));
        when(source.sizeOf(any())).thenReturn((long) zip.length);
        when(sink.upload(anyString(), anyInt(), anyString(), anyLong(), any(), any()))
                .thenAnswer(inv -> new ChildRef("gls-archive-children",
                        inv.getArgument(0) + "/" + inv.getArgument(1) + "-" + inv.getArgument(2),
                        "etag-stub", inv.getArgument(3)));

        ResponseEntity<ExtractResponse> resp = controller.extractArchive(
                validTraceparent(), request("bucket", "test.zip", "node-1"), null);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        ExtractResponse body = resp.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getNodeRunId()).isEqualTo("node-1");
        assertThat(body.getDetectedMimeType()).isEqualTo("application/zip");
        assertThat(body.getArchiveType()).isEqualTo(ExtractResponse.ArchiveTypeEnum.ZIP);
        assertThat(body.getChildCount()).isEqualTo(2);
        assertThat(body.getChildren()).hasSize(2);
        assertThat(body.getChildren().get(0).getFileName()).isEqualTo("a.txt");
        assertThat(body.getChildren().get(0).getDocumentRef().getBucket()).isEqualTo("gls-archive-children");
        assertThat(body.getChildren().get(0).getDocumentRef().getEtag()).isEqualTo("etag-stub");
        verify(sink, times(2)).upload(eq("node-1"), anyInt(), anyString(), anyLong(), any(), any());
    }

    @Test
    void happy_path_unpacks_mbox_and_emits_eml_children() {
        String mbox = "From a@a.test Mon Jan  1 00:00:00 2026\nSubject: one\n\nbody one\n"
                + "From b@b.test Tue Jan  2 00:00:00 2026\nSubject: two\n\nbody two\n";
        byte[] bytes = mbox.getBytes(StandardCharsets.UTF_8);
        when(source.open(any())).thenReturn(new ByteArrayInputStream(bytes));
        when(source.sizeOf(any())).thenReturn((long) bytes.length);
        when(sink.upload(anyString(), anyInt(), anyString(), anyLong(), any(), any()))
                .thenAnswer(inv -> new ChildRef("gls-archive-children",
                        inv.getArgument(0) + "/" + inv.getArgument(1), "e", inv.getArgument(3)));

        ResponseEntity<ExtractResponse> resp = controller.extractArchive(
                validTraceparent(), request("b", "x.mbox", "node-mbox"), null);

        ExtractResponse body = resp.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getArchiveType()).isEqualTo(ExtractResponse.ArchiveTypeEnum.MBOX);
        assertThat(body.getChildren()).hasSize(2);
        assertThat(body.getChildren().get(0).getFileName()).isEqualTo("message-0.eml");
        assertThat(body.getChildren().get(0).getDetectedMimeType()).isEqualTo("message/rfc822");
    }

    @Test
    void document_not_found_propagates_and_emits_FAILED_event() {
        when(source.sizeOf(any())).thenReturn(-1L);
        when(source.open(any()))
                .thenThrow(new DocumentNotFoundException(DocumentRef.of("b", "missing")));

        assertThatThrownBy(() ->
                controller.extractArchive(validTraceparent(), request("b", "missing", "node-nf"), null))
                .isInstanceOf(DocumentNotFoundException.class);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEmitter, times(1)).emit(captor.capture());
        assertThat(captor.getValue().details().metadata())
                .containsEntry("errorCode", "DOCUMENT_NOT_FOUND");
        assertThat(captor.getValue().nodeRunId()).isEqualTo("node-nf");
        assertThat(captor.getValue().outcome()).isEqualTo(Outcome.FAILURE);
    }

    @Test
    void archive_too_large_at_pre_flight_throws_caps_exception() throws IOException {
        byte[] zip = makeZip("a.txt", "alpha");
        when(source.sizeOf(any())).thenReturn(1024L * 1024 * 1024 * 4); // 4GB

        ExtractController tiny = newController(/* maxArchiveBytes */ 1024L,
                /* maxChildren */ 100, /* maxChildBytes */ 1024L);

        assertThatThrownBy(() ->
                tiny.extractArchive(validTraceparent(), request("b", "huge.zip", "node-big"), null))
                .isInstanceOf(ArchiveCapsExceededException.class)
                .hasMessageContaining("source archive size");
    }

    @Test
    void archive_too_many_children_during_walk_throws_caps_exception() throws IOException {
        byte[] zip = makeZip("a.txt", "x", "b.txt", "y", "c.txt", "z");
        when(source.open(any())).thenReturn(new ByteArrayInputStream(zip));
        when(source.sizeOf(any())).thenReturn((long) zip.length);

        ExtractController capped = newController(/* maxArchiveBytes */ Long.MAX_VALUE,
                /* maxChildren */ 1, /* maxChildBytes */ Long.MAX_VALUE);
        when(sink.upload(anyString(), anyInt(), anyString(), anyLong(), any(), any()))
                .thenAnswer(inv -> new ChildRef("b", "k", "e", inv.getArgument(3)));

        assertThatThrownBy(() ->
                capped.extractArchive(validTraceparent(),
                        request("b", "test.zip", "node-many"), null))
                .isInstanceOf(ArchiveCapsExceededException.class);
    }

    @Test
    void successful_unpack_emits_EXTRACTION_COMPLETED_tier_2_audit() throws IOException {
        byte[] zip = makeZip("only.txt", "the contents");
        when(source.open(any())).thenReturn(new ByteArrayInputStream(zip));
        when(source.sizeOf(any())).thenReturn((long) zip.length);
        when(sink.upload(anyString(), anyInt(), anyString(), anyLong(), any(), any()))
                .thenAnswer(inv -> new ChildRef("b", "k", "e", inv.getArgument(3)));

        controller.extractArchive(validTraceparent(),
                request("bucket", "x.zip", "node-audit"), null);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEmitter, times(1)).emit(captor.capture());
        AuditEvent emitted = captor.getValue();
        assertThat(emitted.eventType()).isEqualTo("EXTRACTION_COMPLETED");
        assertThat(emitted.tier()).isEqualTo(Tier.SYSTEM);
        assertThat(emitted.outcome()).isEqualTo(Outcome.SUCCESS);
        assertThat(emitted.action()).isEqualTo("EXTRACT");
        assertThat(emitted.nodeRunId()).isEqualTo("node-audit");
        assertThat(emitted.details().metadata()).containsEntry("archiveType", "zip");
        assertThat(emitted.details().metadata()).containsEntry("childCount", 1);
    }

    @Test
    void in_flight_idempotency_short_circuits_without_calling_source() {
        when(idempotency.tryAcquire("node-busy")).thenReturn(IdempotencyOutcome.inFlight());

        assertThatThrownBy(() ->
                controller.extractArchive(validTraceparent(),
                        request("b", "k", "node-busy"), null))
                .isInstanceOf(IdempotencyInFlightException.class);

        verify(source, never()).open(any());
        verify(sink, never()).upload(anyString(), anyInt(), anyString(), anyLong(), any(), any());
    }

    @Test
    void cached_idempotency_returns_stored_response_without_walking() throws Exception {
        ExtractResponse cached = new ExtractResponse();
        cached.setNodeRunId("node-c");
        cached.setDetectedMimeType("application/zip");
        cached.setArchiveType(ExtractResponse.ArchiveTypeEnum.ZIP);
        cached.setChildCount(0);
        cached.setChildren(List.of());
        cached.setDurationMs(0);
        cached.setCostUnits(0);
        String json = mapper.writeValueAsString(cached);
        when(idempotency.tryAcquire("node-c")).thenReturn(IdempotencyOutcome.cached(json));

        ResponseEntity<ExtractResponse> resp = controller.extractArchive(
                validTraceparent(), request("b", "k", "node-c"), null);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getNodeRunId()).isEqualTo("node-c");
        verify(source, never()).open(any());
        verify(auditEmitter, never()).emit(any());
    }

    @Test
    void successful_unpack_caches_the_response_for_subsequent_retries() throws IOException {
        byte[] zip = makeZip("only.txt", "x");
        when(source.open(any())).thenReturn(new ByteArrayInputStream(zip));
        when(source.sizeOf(any())).thenReturn((long) zip.length);
        when(sink.upload(anyString(), anyInt(), anyString(), anyLong(), any(), any()))
                .thenAnswer(inv -> new ChildRef("b", "k", "e", inv.getArgument(3)));

        controller.extractArchive(validTraceparent(),
                request("b", "k", "node-cache"), null);

        verify(idempotency, times(1)).cacheResult(eq("node-cache"), any());
    }

    @Test
    void failure_releases_the_idempotency_row_so_retries_can_proceed() {
        when(source.sizeOf(any())).thenReturn(-1L);
        when(source.open(any())).thenThrow(new DocumentNotFoundException(DocumentRef.of("b", "missing")));

        assertThatThrownBy(() ->
                controller.extractArchive(validTraceparent(),
                        request("b", "missing", "node-fail"), null))
                .isInstanceOf(DocumentNotFoundException.class);

        verify(idempotency, times(1)).releaseOnFailure("node-fail");
        verify(idempotency, never()).cacheResult(any(), any());
    }

    @Test
    void controller_implements_generated_api() {
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

    private static byte[] makeZip(String... nameAndBody) throws IOException {
        if (nameAndBody.length % 2 != 0) {
            throw new IllegalArgumentException("expected name/body pairs");
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream out = new ZipOutputStream(bos)) {
            for (int i = 0; i < nameAndBody.length; i += 2) {
                out.putNextEntry(new ZipEntry(nameAndBody[i]));
                out.write(nameAndBody[i + 1].getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
        }
        return bos.toByteArray();
    }
}
