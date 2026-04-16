package co.uk.wolfnotsheep.infrastructure.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Notifies the MCP server to evict its caches after governance data changes.
 * Fire-and-forget — cache eviction failure is non-critical (TTL will expire anyway).
 */
@Service
public class McpCacheEvictionService {

    private static final Logger log = LoggerFactory.getLogger(McpCacheEvictionService.class);

    private final String mcpServerUrl;
    private final HttpClient httpClient;

    public McpCacheEvictionService(
            @Value("${mcp.server.url:http://mcp-server:8081}") String mcpServerUrl) {
        this.mcpServerUrl = mcpServerUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    /**
     * Evict all MCP caches. Called after any governance admin write.
     */
    public void evictAll() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(mcpServerUrl + "/api/internal/cache/evict-all"))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .timeout(Duration.ofSeconds(5))
                    .build();
            httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                    .thenAccept(resp -> {
                        if (resp.statusCode() == 200) {
                            log.debug("[McpCache] Evicted all MCP caches");
                        } else {
                            log.warn("[McpCache] Eviction returned status {}", resp.statusCode());
                        }
                    })
                    .exceptionally(e -> {
                        log.debug("[McpCache] Eviction failed (non-critical): {}", e.getMessage());
                        return null;
                    });
        } catch (Exception e) {
            log.debug("[McpCache] Eviction request failed (non-critical): {}", e.getMessage());
        }
    }
}
