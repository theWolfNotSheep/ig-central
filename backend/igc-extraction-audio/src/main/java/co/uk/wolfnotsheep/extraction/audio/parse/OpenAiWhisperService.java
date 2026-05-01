package co.uk.wolfnotsheep.extraction.audio.parse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.observation.annotation.Observed;
import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Random;

/**
 * OpenAI Whisper backend. POSTs {@code multipart/form-data} to
 * {@code api.openai.com/v1/audio/transcriptions} with model
 * {@code whisper-1}. Auth via {@code OPENAI_API_KEY}.
 *
 * <p>The materialised temp file approach is forced by the multipart
 * shape — the API wants {@code file} as a form field with a filename;
 * streaming the body without a known content-length doesn't fit
 * {@link HttpClient}'s publishers cleanly. Temp file is deleted in a
 * {@code finally} block.
 *
 * <p>Failure modes:
 *
 * <ul>
 *     <li>HTTP 4xx → {@link AudioCorruptException} (the audio bytes
 *         or request shape were rejected). Maps to 422.</li>
 *     <li>HTTP 5xx / IO → {@link UncheckedIOException} (mapped to
 *         503 by the controller's source-unavailable handler).</li>
 *     <li>Missing {@code OPENAI_API_KEY} at construction → throws
 *         {@link IllegalStateException}; the wiring config falls back
 *         to {@link NotConfiguredAudioTranscriptionService} when this
 *         happens, so callers see {@code AUDIO_NOT_CONFIGURED}
 *         rather than a hard startup failure.</li>
 * </ul>
 */
public class OpenAiWhisperService implements AudioTranscriptionService {

    private static final Logger log = LoggerFactory.getLogger(OpenAiWhisperService.class);
    private static final String DEFAULT_MODEL = "whisper-1";

    private final HttpClient http;
    private final URI endpoint;
    private final String apiKey;
    private final String model;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Tika tika = new Tika();

    public OpenAiWhisperService(String apiKey, String endpoint, String model, Duration timeout) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("OPENAI_API_KEY is required");
        }
        this.apiKey = apiKey;
        this.endpoint = URI.create(endpoint == null || endpoint.isBlank()
                ? "https://api.openai.com/v1/audio/transcriptions" : endpoint);
        this.model = (model == null || model.isBlank()) ? DEFAULT_MODEL : model;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    @Override
    public String providerId() {
        return "openai-whisper";
    }

    @Override
    public boolean isReady() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    @Observed(name = "audio.transcribe", contextualName = "audio-transcribe",
            lowCardinalityKeyValues = {"component", "openai-whisper"})
    public AudioResult transcribe(InputStream input, String fileName, long size,
                                  String language, String prompt) {
        Path temp;
        long bytes;
        String detectedMime;
        try {
            temp = Files.createTempFile("igc-audio-", suffixFor(fileName));
            try (input) {
                bytes = Files.copy(input, temp, StandardCopyOption.REPLACE_EXISTING);
            }
            detectedMime = tika.detect(temp.toFile());
        } catch (IOException e) {
            throw new UncheckedIOException("audio temp materialisation failed", e);
        }

        try {
            String boundary = "----igcAudio" + Math.abs(new Random().nextLong());
            byte[] body = buildMultipart(temp, fileName == null ? "audio.bin" : fileName,
                    detectedMime == null ? "application/octet-stream" : detectedMime,
                    language, prompt, boundary);
            HttpRequest req = HttpRequest.newBuilder(endpoint)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(BodyPublishers.ofByteArray(body))
                    .timeout(Duration.ofMinutes(10))
                    .build();
            HttpResponse<String> resp = http.send(req, BodyHandlers.ofString(StandardCharsets.UTF_8));
            int code = resp.statusCode();
            if (code >= 200 && code < 300) {
                JsonNode json = mapper.readTree(resp.body());
                String text = json.path("text").asText("");
                return new AudioResult(text, detectedMime, language, null, bytes, providerId());
            }
            if (code >= 400 && code < 500) {
                throw new AudioCorruptException("OpenAI rejected the request (" + code + "): "
                        + resp.body());
            }
            throw new UncheckedIOException("OpenAI Whisper " + code + ": " + resp.body(),
                    new IOException("HTTP " + code));
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new UncheckedIOException("OpenAI Whisper call failed",
                    e instanceof IOException io ? io : new IOException(e));
        } finally {
            try {
                Files.deleteIfExists(temp);
            } catch (IOException e) {
                log.warn("audio temp file cleanup failed: {} ({})", temp, e.getMessage());
            }
        }
    }

    private byte[] buildMultipart(Path file, String fileName, String contentType,
                                  String language, String prompt, String boundary) throws IOException {
        var out = new java.io.ByteArrayOutputStream();
        appendField(out, boundary, "model", model);
        appendField(out, boundary, "response_format", "json");
        if (language != null && !language.isBlank()) {
            appendField(out, boundary, "language", language);
        }
        if (prompt != null && !prompt.isBlank()) {
            appendField(out, boundary, "prompt", prompt);
        }
        appendFile(out, boundary, fileName, contentType, Files.readAllBytes(file));
        out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    private static void appendField(java.io.ByteArrayOutputStream out, String boundary,
                                    String name, String value) throws IOException {
        out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8));
        out.write(value.getBytes(StandardCharsets.UTF_8));
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private static void appendFile(java.io.ByteArrayOutputStream out, String boundary,
                                   String fileName, String contentType, byte[] bytes) throws IOException {
        out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"file\"; filename=\""
                + fileName + "\"\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(bytes);
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private static String suffixFor(String fileName) {
        if (fileName == null) return ".bin";
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) return ".bin";
        String ext = fileName.substring(dot);
        return ext.length() > 6 ? ".bin" : ext;
    }
}
