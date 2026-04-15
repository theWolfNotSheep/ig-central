package co.uk.wolfnotsheep.mcp;

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
 * Emits pipeline log events when MCP tools are called by the LLM.
 * This lets the UI show the LLM's reasoning process in real-time:
 * which tools it calls, in what order, and what it decides.
 */
@Component
public class ToolCallLogger {

    private static final Logger log = LoggerFactory.getLogger(ToolCallLogger.class);

    private final HttpClient httpClient;
    private final String apiBaseUrl;

    public ToolCallLogger(@Value("${pipeline.api-base-url:http://localhost:8080}") String apiBaseUrl) {
        this.apiBaseUrl = apiBaseUrl;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    }

    /**
     * Log a tool call event. Fire-and-forget — never blocks the tool execution.
     */
    public void logToolCall(String documentId, String toolName, String detail) {
        try {
            String json = "{\"documentId\":\"%s\",\"fileName\":\"\",\"stage\":\"LLM_TOOL\",\"level\":\"INFO\",\"message\":\"%s: %s\",\"durationMs\":0}"
                    .formatted(esc(documentId), esc(toolName), esc(detail));

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(apiBaseUrl + "/api/internal/pipeline/log"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(2))
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            httpClient.sendAsync(req, HttpResponse.BodyHandlers.discarding());
        } catch (Exception e) {
            log.debug("Tool call log failed (non-critical): {}", e.getMessage());
        }
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ");
    }
}
