package co.uk.wolfnotsheep.infrastructure.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * SSE broadcaster for real-time pipeline updates.
 * Polls MonitoringService on a fast interval and pushes changes
 * to all connected SSE clients. Also accepts direct broadcasts
 * for immediate document-level status events.
 */
@Component
public class PipelineEventBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(PipelineEventBroadcaster.class);

    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final JsonMapper jsonMapper;
    private final MonitoringService monitoringService;

    public PipelineEventBroadcaster(JsonMapper jsonMapper, MonitoringService monitoringService) {
        this.jsonMapper = jsonMapper;
        this.monitoringService = monitoringService;
    }

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(0L); // no timeout
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));

        // Send initial snapshot so the client has data immediately
        try {
            emitter.send(SseEmitter.event().name("connected").data("ok"));
            String metrics = jsonMapper.writeValueAsString(monitoringService.getPipelineMetrics());
            emitter.send(SseEmitter.event().name("pipeline-metrics").data(metrics));
        } catch (IOException e) {
            emitters.remove(emitter);
        }

        log.debug("SSE client connected ({} total)", emitters.size());
        return emitter;
    }

    /**
     * Push pipeline metrics to all clients every 5 seconds.
     * Skips the DB call entirely if no clients are connected.
     */
    @Scheduled(fixedRate = 5000)
    public void pushMetrics() {
        if (emitters.isEmpty()) return;
        try {
            String json = jsonMapper.writeValueAsString(monitoringService.getPipelineMetrics());
            broadcast("pipeline-metrics", json);
        } catch (Exception e) {
            log.warn("Failed to push metrics: {}", e.getMessage());
        }
    }

    /**
     * Send a heartbeat every 15 seconds to keep connections alive through proxies.
     */
    @Scheduled(fixedRate = 15000)
    public void heartbeat() {
        if (emitters.isEmpty()) return;
        broadcast("heartbeat", "\"ping\"");
    }

    /**
     * Broadcast a named event to all connected clients.
     */
    public void broadcast(String eventName, Object data) {
        if (emitters.isEmpty()) return;

        String json;
        try {
            json = data instanceof String ? (String) data : jsonMapper.writeValueAsString(data);
        } catch (Exception e) {
            log.warn("Failed to serialize SSE event: {}", e.getMessage());
            return;
        }

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(json));
            } catch (Exception e) {
                emitters.remove(emitter);
            }
        }
    }

    /**
     * Broadcast a document status change event (called by the internal webhook).
     */
    public void broadcastDocumentStatus(String documentId, String status, String fileName) {
        broadcast("document-status", Map.of(
                "documentId", documentId,
                "status", status,
                "fileName", fileName != null ? fileName : ""
        ));
    }

    /**
     * Broadcast a pipeline processing log event (called by consumers at each stage).
     */
    public void broadcastPipelineLog(String documentId, String fileName, String stage,
                                      String level, String message, Long durationMs) {
        broadcast("pipeline-log", Map.of(
                "documentId", documentId,
                "fileName", fileName != null ? fileName : "",
                "stage", stage,
                "level", level,
                "message", message,
                "durationMs", durationMs != null ? durationMs : 0,
                "timestamp", System.currentTimeMillis()
        ));
    }

    public int getClientCount() {
        return emitters.size();
    }
}
