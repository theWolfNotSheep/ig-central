package co.uk.wolfnotsheep.infrastructure.controllers.admin;

import co.uk.wolfnotsheep.platform.config.services.AppConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * Proxy to the Ollama REST API for model management from the admin UI.
 * Ollama runs on the host — this controller proxies requests from the
 * browser (which can't reach host.docker.internal directly).
 */
@RestController
@RequestMapping("/api/admin/ollama")
public class OllamaController {

    private static final Logger log = LoggerFactory.getLogger(OllamaController.class);

    private final AppConfigService configService;
    private final HttpClient httpClient;

    public OllamaController(AppConfigService configService) {
        this.configService = configService;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    private String getBaseUrl() {
        String envUrl = System.getenv("OLLAMA_BASE_URL");
        String fallback = envUrl != null ? envUrl : "http://localhost:11434";
        return configService.getValue("llm.ollama.base_url", fallback);
    }

    /**
     * List installed models.
     */
    @GetMapping("/models")
    public ResponseEntity<String> listModels() {
        try {
            HttpResponse<String> resp = httpClient.send(
                    HttpRequest.newBuilder().uri(URI.create(getBaseUrl() + "/api/tags")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            return ResponseEntity.status(resp.statusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(resp.body());
        } catch (Exception e) {
            log.warn("Ollama not reachable: {}", e.getMessage());
            return ResponseEntity.ok("{\"models\":[],\"error\":\"" + errMsg(e) + "\"}");
        }
    }

    /**
     * Get details about a specific model.
     */
    @PostMapping("/show")
    public ResponseEntity<String> showModel(@RequestBody Map<String, String> body) {
        try {
            HttpResponse<String> resp = httpClient.send(
                    HttpRequest.newBuilder()
                            .uri(URI.create(getBaseUrl() + "/api/show"))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString("{\"model\":\"" + body.get("model") + "\"}"))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            return ResponseEntity.status(resp.statusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(resp.body());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("{\"error\":\"" + errMsg(e) + "\"}");
        }
    }

    /**
     * Pull (download) a model. Streams progress via SSE.
     */
    @GetMapping(value = "/pull", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter pullModel(@RequestParam String model) {
        SseEmitter emitter = new SseEmitter(600_000L); // 10 min timeout for large models

        Thread.startVirtualThread(() -> {
            try {
                log.info("Pulling Ollama model: {}", model);
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(getBaseUrl() + "/api/pull"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{\"model\":\"" + model + "\",\"stream\":true}"))
                        .build();

                HttpResponse<java.io.InputStream> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofInputStream());

                try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(resp.body()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (!line.isBlank()) {
                            emitter.send(SseEmitter.event().name("progress").data(line));
                        }
                    }
                }
                emitter.send(SseEmitter.event().name("done").data("{\"status\":\"success\"}"));
                emitter.complete();
                log.info("Ollama model pull complete: {}", model);
            } catch (Exception e) {
                log.error("Ollama pull failed for {}: {}", model, e.getMessage());
                try {
                    emitter.send(SseEmitter.event().name("error").data("{\"error\":\"" + errMsg(e) + "\"}"));
                    emitter.complete();
                } catch (Exception sseErr) { log.warn("Failed to send SSE error for Ollama pull: {}", sseErr.getMessage()); }
            }
        });

        return emitter;
    }

    /**
     * Delete a model.
     */
    @DeleteMapping("/models")
    public ResponseEntity<String> deleteModel(@RequestBody Map<String, String> body) {
        try {
            HttpResponse<String> resp = httpClient.send(
                    HttpRequest.newBuilder()
                            .uri(URI.create(getBaseUrl() + "/api/delete"))
                            .header("Content-Type", "application/json")
                            .method("DELETE", HttpRequest.BodyPublishers.ofString("{\"model\":\"" + body.get("model") + "\"}"))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            log.info("Deleted Ollama model: {}", body.get("model"));
            return ResponseEntity.status(resp.statusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(resp.body());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("{\"error\":\"" + errMsg(e) + "\"}");
        }
    }

    /**
     * Check if Ollama is reachable.
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        try {
            HttpResponse<String> resp = httpClient.send(
                    HttpRequest.newBuilder().uri(URI.create(getBaseUrl() + "/api/tags"))
                            .timeout(Duration.ofSeconds(3)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            return ResponseEntity.ok(Map.of("reachable", true, "url", getBaseUrl()));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("reachable", false, "url", getBaseUrl(), "error", errMsg(e)));
        }
    }

    private static String errMsg(Exception e) {
        String msg = e.getMessage();
        if (msg == null) msg = e.getClass().getSimpleName();
        return msg.replace("\"", "'");
    }
}
