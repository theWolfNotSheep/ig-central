package co.uk.wolfnotsheep.infrastructure.config;

import co.uk.wolfnotsheep.infrastructure.services.McpCacheEvictionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Evicts MCP caches after any successful write to governance admin endpoints.
 * Fire-and-forget: does not block the response.
 */
@Component
public class McpCacheEvictionInterceptor implements HandlerInterceptor {

    private final McpCacheEvictionService mcpCacheEviction;

    public McpCacheEvictionInterceptor(McpCacheEvictionService mcpCacheEviction) {
        this.mcpCacheEviction = mcpCacheEviction;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        if (ex != null) return; // Don't evict on errors
        String method = request.getMethod();
        if ("POST".equals(method) || "PUT".equals(method) || "DELETE".equals(method)) {
            if (response.getStatus() >= 200 && response.getStatus() < 300) {
                mcpCacheEviction.evictAll();
            }
        }
    }
}
