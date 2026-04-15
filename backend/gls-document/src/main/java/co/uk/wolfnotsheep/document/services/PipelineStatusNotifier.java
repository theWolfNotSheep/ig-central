package co.uk.wolfnotsheep.document.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Fire-and-forget notifier that tells the API about document status changes
 * so SSE clients get near-instant updates. If the API is unreachable,
 * it silently fails — the 2-second polling fallback will catch it.
 */
@Component
public class PipelineStatusNotifier {

    private static final Logger log = LoggerFactory.getLogger(PipelineStatusNotifier.class);

    private final HttpClient httpClient;
    private final String apiBaseUrl;

    public PipelineStatusNotifier(
            @Value("${pipeline.api-base-url:http://localhost:8080}") String apiBaseUrl) {
        this.apiBaseUrl = apiBaseUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
    }

    public void notifyStatusChange(String documentId, String status, String fileName) {
        postInternal("/api/internal/pipeline/status-changed",
                "{\"documentId\":\"%s\",\"status\":\"%s\",\"fileName\":\"%s\"}"
                        .formatted(documentId, status, esc(fileName)));
    }

    /**
     * Send a pipeline log event for display in the monitoring UI.
     * @param stage EXTRACTION, PII_SCAN, CLASSIFICATION, ENFORCEMENT
     * @param level INFO, WARN, ERROR
     * @param message human-readable description
     * @param durationMs how long this step took (null if not completed)
     */
    public void emitLog(String documentId, String fileName, String stage,
                        String level, String message, Long durationMs) {
        postInternal("/api/internal/pipeline/log",
                "{\"documentId\":\"%s\",\"fileName\":\"%s\",\"stage\":\"%s\",\"level\":\"%s\",\"message\":\"%s\",\"durationMs\":%d}"
                        .formatted(documentId, esc(fileName), stage, level, esc(message),
                                durationMs != null ? durationMs : 0));
    }

    private void postInternal(String path, String json) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(apiBaseUrl + path))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(2))
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            httpClient.sendAsync(req, HttpResponse.BodyHandlers.discarding());
        } catch (Exception e) {
            log.warn("Pipeline notification failed: {}", e.getMessage());
        }
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ");
    }
}
