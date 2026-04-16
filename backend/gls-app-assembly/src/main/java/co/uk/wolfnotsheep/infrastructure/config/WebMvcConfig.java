package co.uk.wolfnotsheep.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final McpCacheEvictionInterceptor mcpCacheEvictionInterceptor;

    public WebMvcConfig(McpCacheEvictionInterceptor mcpCacheEvictionInterceptor) {
        this.mcpCacheEvictionInterceptor = mcpCacheEvictionInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Evict MCP caches on any successful write to paths that mutate
        // governance data or create correction/feedback records. Without this,
        // the MCP server's @Cacheable PII/correction lookups return stale data
        // for up to 5 minutes after a user dismisses PII or overrides a
        // classification — so the LLM keeps repeating the same mistake.
        registry.addInterceptor(mcpCacheEvictionInterceptor)
                .addPathPatterns(
                        "/api/admin/governance/**",
                        "/api/documents/*/pii/*/dismiss",
                        "/api/review/**"
                );
    }
}
