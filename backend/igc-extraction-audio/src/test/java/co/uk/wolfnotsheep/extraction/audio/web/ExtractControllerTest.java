package co.uk.wolfnotsheep.extraction.audio.web;

import co.uk.wolfnotsheep.extraction.audio.api.ExtractionApi;
import co.uk.wolfnotsheep.extraction.audio.jobs.JobAcquisition;
import co.uk.wolfnotsheep.extraction.audio.jobs.JobRecord;
import co.uk.wolfnotsheep.extraction.audio.jobs.JobState;
import co.uk.wolfnotsheep.extraction.audio.jobs.JobStore;
import co.uk.wolfnotsheep.extraction.audio.model.ExtractRequest;
import co.uk.wolfnotsheep.extraction.audio.model.ExtractRequestDocumentRef;
import co.uk.wolfnotsheep.extraction.audio.model.ExtractResponse;
import co.uk.wolfnotsheep.extraction.audio.model.ExtractResponseTextOneOf;
import co.uk.wolfnotsheep.extraction.audio.model.JobAccepted;
import co.uk.wolfnotsheep.extraction.audio.parse.AudioCorruptException;
import co.uk.wolfnotsheep.extraction.audio.parse.AudioNotConfiguredException;
import co.uk.wolfnotsheep.extraction.audio.parse.AudioResult;
import co.uk.wolfnotsheep.extraction.audio.parse.AudioTranscriptionService;
import co.uk.wolfnotsheep.extraction.audio.sink.DocumentSink;
import co.uk.wolfnotsheep.extraction.audio.source.DocumentNotFoundException;
import co.uk.wolfnotsheep.extraction.audio.source.DocumentRef;
import co.uk.wolfnotsheep.extraction.audio.source.DocumentSource;
import co.uk.wolfnotsheep.platformaudit.emit.AuditEmitter;
import co.uk.wolfnotsheep.platformaudit.envelope.AuditEvent;
import co.uk.wolfnotsheep.platformaudit.envelope.Outcome;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExtractControllerTest {

    private DocumentSource source;
    private AudioTranscriptionService backend;
    private DocumentSink sink;
    private JobStore jobs;
    private ObjectMapper mapper;
    private AuditEmitter auditEmitter;
    private AsyncDispatcher asyncDispatcher;
    private ExtractController controller;

    @BeforeEach
    void setUp() {
        source = mock(DocumentSource.class);
        backend = mock(AudioTranscriptionService.class);
        when(backend.providerId()).thenReturn("openai-whisper");
        sink = mock(DocumentSink.class);
        jobs = mock(JobStore.class);
        when(jobs.tryAcquire(any())).thenReturn(JobAcquisition.acquired());
        when(source.sizeOf(any())).thenReturn(-1L);
        mapper = new ObjectMapper();
        auditEmitter = mock(AuditEmitter.class);
        asyncDispatcher = mock(AsyncDispatcher.class);
        controller = new ExtractController(
                source, backend, sink, jobs,
                new ExtractMetrics(new SimpleMeterRegistry()), mapper,
                providerOf(auditEmitter), asyncDispatcher,
                /* inlineByteCeiling */ 262_144L,
                /* maxSourceBytes */ 524_288_000L,
                "igc-extraction-audio", "0.0.1-SNAPSHOT", "test-instance");
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<AuditEmitter> providerOf(AuditEmitter emitter) {
        ObjectProvider<AuditEmitter> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(emitter);
        return provider;
    }

    @Test
    void sync_happy_path_returns_200_with_inline_text() {
        when(source.open(any())).thenReturn(audio());
        when(backend.transcribe(any(InputStream.class), any(), anyLong(), any(), any()))
                .thenReturn(new AudioResult("hello", "audio/mpeg", "en", 1.5f, 4096L, "openai-whisper"));

        ResponseEntity<ExtractResponse> resp = controller.extractAudio(
                validTraceparent(), request("bucket", "doc.mp3", "node-1"), null, null);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        ExtractResponse body = resp.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getNodeRunId()).isEqualTo("node-1");
        assertThat(body.getProvider()).isEqualTo("openai-whisper");
        assertThat(body.getText()).isInstanceOf(ExtractResponseTextOneOf.class);
        assertThat(((ExtractResponseTextOneOf) body.getText()).getText()).isEqualTo("hello");
        verify(jobs).markRunning("node-1");
        verify(jobs).markCompleted(anyString(), anyString());
    }

    @Test
    void async_path_returns_202_with_Location_header_and_dispatches() {
        ResponseEntity<ExtractResponse> resp = controller.extractAudio(
                validTraceparent(), request("b", "k", "node-async"), null, "respond-async");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(resp.getHeaders().getFirst(HttpHeaders.LOCATION)).isEqualTo("/v1/jobs/node-async");
        Object body = resp.getBody();
        assertThat(body).isInstanceOf(JobAccepted.class);
        JobAccepted accepted = (JobAccepted) body;
        assertThat(accepted.getNodeRunId()).isEqualTo("node-async");
        assertThat(accepted.getStatus()).isEqualTo(JobAccepted.StatusEnum.PENDING);
        verify(asyncDispatcher).dispatch(any(), any());
        verify(source, never()).open(any());
    }

    @Test
    void document_not_found_emits_FAILED_audit() {
        when(source.open(any())).thenThrow(new DocumentNotFoundException(DocumentRef.of("b", "x")));

        assertThatThrownBy(() -> controller.extractAudio(validTraceparent(),
                request("b", "x", "node-nf"), null, null))
                .isInstanceOf(DocumentNotFoundException.class);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEmitter, times(1)).emit(captor.capture());
        assertThat(captor.getValue().outcome()).isEqualTo(Outcome.FAILURE);
        assertThat(captor.getValue().details().metadata())
                .containsEntry("errorCode", "DOCUMENT_NOT_FOUND");
        verify(jobs).markFailed("node-nf", "DOCUMENT_NOT_FOUND",
                "document not found: b/x");
    }

    @Test
    void backend_not_configured_propagates_AUDIO_NOT_CONFIGURED() {
        when(source.open(any())).thenReturn(audio());
        when(backend.transcribe(any(InputStream.class), any(), anyLong(), any(), any()))
                .thenThrow(new AudioNotConfiguredException("no backend"));

        assertThatThrownBy(() -> controller.extractAudio(validTraceparent(),
                request("b", "k", "node-nc"), null, null))
                .isInstanceOf(AudioNotConfiguredException.class);

        verify(jobs).markFailed("node-nc", "AUDIO_NOT_CONFIGURED", "no backend");
    }

    @Test
    void corrupt_audio_propagates_AUDIO_CORRUPT() {
        when(source.open(any())).thenReturn(audio());
        when(backend.transcribe(any(InputStream.class), any(), anyLong(), any(), any()))
                .thenThrow(new AudioCorruptException("bad bytes"));

        assertThatThrownBy(() -> controller.extractAudio(validTraceparent(),
                request("b", "k", "node-corrupt"), null, null))
                .isInstanceOf(AudioCorruptException.class);
    }

    @Test
    void source_too_large_throws_DocumentTooLarge() {
        when(source.sizeOf(any())).thenReturn(1_000_000_000L);

        assertThatThrownBy(() -> controller.extractAudio(validTraceparent(),
                request("b", "k", "node-big"), null, null))
                .isInstanceOf(DocumentTooLargeException.class);
    }

    @Test
    void in_flight_running_collision_returns_409_for_sync() {
        JobRecord existing = new JobRecord("node-busy", JobState.RUNNING,
                Instant.now(), Instant.now(), null, null, null, null, Instant.now().plusSeconds(3600));
        when(jobs.tryAcquire("node-busy")).thenReturn(JobAcquisition.running(existing));

        assertThatThrownBy(() -> controller.extractAudio(validTraceparent(),
                request("b", "k", "node-busy"), null, null))
                .isInstanceOf(JobInFlightException.class);
    }

    @Test
    void in_flight_running_collision_returns_202_for_async() {
        JobRecord existing = new JobRecord("node-busy", JobState.RUNNING,
                Instant.now(), Instant.now(), null, null, null, null, Instant.now().plusSeconds(3600));
        when(jobs.tryAcquire("node-busy")).thenReturn(JobAcquisition.running(existing));

        ResponseEntity<ExtractResponse> resp = controller.extractAudio(validTraceparent(),
                request("b", "k", "node-busy"), null, "respond-async");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(resp.getHeaders().getFirst(HttpHeaders.LOCATION)).isEqualTo("/v1/jobs/node-busy");
        verify(asyncDispatcher, never()).dispatch(any(), any());
    }

    @Test
    void completed_idempotency_returns_cached_for_sync() throws Exception {
        ExtractResponse cached = new ExtractResponse();
        cached.setNodeRunId("node-c");
        cached.setDetectedMimeType("audio/mpeg");
        ExtractResponseTextOneOf inline = new ExtractResponseTextOneOf();
        inline.setText("from cache");
        inline.setEncoding(ExtractResponseTextOneOf.EncodingEnum.UTF_8);
        cached.setText(inline);
        cached.setDurationMs(0);
        cached.setCostUnits(0);
        String json = mapper.writeValueAsString(cached);
        JobRecord row = new JobRecord("node-c", JobState.COMPLETED,
                Instant.now(), Instant.now(), Instant.now(), json, null, null,
                Instant.now().plusSeconds(3600));
        when(jobs.tryAcquire("node-c")).thenReturn(JobAcquisition.completed(row));

        ResponseEntity<ExtractResponse> resp = controller.extractAudio(
                validTraceparent(), request("b", "k", "node-c"), null, null);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().getNodeRunId()).isEqualTo("node-c");
        verify(source, never()).open(any());
        verify(auditEmitter, never()).emit(any());
    }

    @Test
    void completed_idempotency_returns_202_for_async_replay() {
        JobRecord row = new JobRecord("node-c", JobState.COMPLETED,
                Instant.now(), Instant.now(), Instant.now(), "{}", null, null,
                Instant.now().plusSeconds(3600));
        when(jobs.tryAcquire("node-c")).thenReturn(JobAcquisition.completed(row));

        ResponseEntity<ExtractResponse> resp = controller.extractAudio(
                validTraceparent(), request("b", "k", "node-c"), null, "respond-async");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        verify(asyncDispatcher, never()).dispatch(any(), any());
    }

    @Test
    void controller_implements_generated_api() {
        assertThat(ExtractionApi.class.isAssignableFrom(ExtractController.class)).isTrue();
    }

    private static InputStream audio() {
        return new ByteArrayInputStream("audio".getBytes(StandardCharsets.UTF_8));
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
