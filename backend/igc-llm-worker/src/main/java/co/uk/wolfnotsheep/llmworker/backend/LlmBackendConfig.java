package co.uk.wolfnotsheep.llmworker.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the {@link LlmService} bean. Selection order:
 *
 * <ol>
 *     <li>{@code igc.llm.worker.backend=anthropic} +
 *         {@link AnthropicChatModel} bean is present (Spring AI's
 *         starter autoconfigures it when {@code ANTHROPIC_API_KEY}
 *         is set) → {@link AnthropicLlmService}.</li>
 *     <li>{@code igc.llm.worker.backend=ollama} +
 *         {@link OllamaChatModel} bean is present →
 *         {@link OllamaLlmService}.</li>
 *     <li>Default / misconfigured → {@link NotConfiguredLlmService}.</li>
 * </ol>
 *
 * <p>Same shape as {@code SlmBackendConfig}. The MCP integration
 * matches CSV #1 — every backend gets the configured
 * {@link ToolCallbackProvider} beans handed to its ChatClient
 * builder.
 */
@Configuration
public class LlmBackendConfig {

    private static final Logger log = LoggerFactory.getLogger(LlmBackendConfig.class);

    @Bean
    @ConditionalOnMissingBean(LlmService.class)
    public LlmService llmService(
            @Value("${igc.llm.worker.backend:none}") String backend,
            ObjectProvider<AnthropicChatModel> anthropicProvider,
            ObjectProvider<OllamaChatModel> ollamaProvider,
            ObjectProvider<PromptBlockResolver> blockResolverProvider,
            ObjectProvider<ToolCallbackProvider> toolCallbackProviderProvider,
            @Value("${igc.llm.worker.anthropic.model:claude-sonnet-4-5}") String anthropicModel,
            @Value("${igc.llm.worker.anthropic.temperature:0.1}") double anthropicTemperature,
            @Value("${igc.llm.worker.anthropic.max-tokens:4096}") int anthropicMaxTokens,
            @Value("${igc.llm.worker.ollama.model:qwen2.5:32b}") String ollamaModel,
            @Value("${igc.llm.worker.ollama.temperature:0.1}") double ollamaTemperature,
            @Value("${igc.llm.worker.ollama.num-ctx:32768}") int ollamaNumCtx,
            @Value("${igc.llm.worker.circuit-breaker.enabled:true}") boolean circuitBreakerEnabled,
            @Value("${igc.llm.worker.circuit-breaker.failure-threshold:5}") int circuitBreakerFailureThreshold,
            @Value("${igc.llm.worker.circuit-breaker.open-cooldown:PT30S}") java.time.Duration circuitBreakerOpenCooldown,
            @Value("${igc.llm.worker.mcp.confidence-cap.enabled:true}") boolean mcpCapEnabled,
            @Value("${igc.llm.worker.mcp.confidence-cap.max-confidence:0.7}") float mcpCapMaxConfidence,
            @Value("${igc.llm.worker.fallback.enabled:false}") boolean fallbackEnabled,
            ObjectProvider<McpAvailabilityProbe> mcpAvailabilityProbeProvider,
            ObjectProvider<MeterRegistry> meterRegistryProvider,
            ObjectProvider<ObjectMapper> mapperProvider) {

        ObjectMapper mapper = mapperProvider.getIfAvailable(ObjectMapper::new);
        PromptBlockResolver resolver = blockResolverProvider.getIfAvailable();
        ToolCallbackProvider[] toolCallbacks = toolCallbackProviderProvider
                .stream().toArray(ToolCallbackProvider[]::new);
        if (toolCallbacks.length > 0) {
            log.info("llm: MCP integration active — {} ToolCallbackProvider bean(s) injected",
                    toolCallbacks.length);
        } else {
            log.info("llm: no ToolCallbackProvider beans found — MCP tools will not be available to the LLM call. Set spring.ai.mcp.client.sse.connections.<name>.url to enable.");
        }

        if (resolver == null) {
            log.warn("llm: PromptBlockResolver bean is missing — Mongo not wired? Falling back to not-configured.");
            return new NotConfiguredLlmService();
        }

        // Build per-backend services lazily — only the ones whose ChatModel
        // beans are actually wired up. With fallback enabled and both
        // backends configured, the result is a Fallback(primary, secondary).
        LlmService anthropicService = buildAnthropicIfAvailable(
                anthropicProvider, resolver, anthropicModel, anthropicTemperature, anthropicMaxTokens,
                mapper, toolCallbacks, circuitBreakerEnabled, circuitBreakerFailureThreshold,
                circuitBreakerOpenCooldown, meterRegistryProvider);
        LlmService ollamaService = buildOllamaIfAvailable(
                ollamaProvider, resolver, ollamaModel, ollamaTemperature, ollamaNumCtx,
                mapper, toolCallbacks, circuitBreakerEnabled, circuitBreakerFailureThreshold,
                circuitBreakerOpenCooldown, meterRegistryProvider);

        LlmService primary;
        LlmService secondary;
        if ("anthropic".equalsIgnoreCase(backend)) {
            primary = anthropicService;
            secondary = ollamaService;
        } else if ("ollama".equalsIgnoreCase(backend)) {
            primary = ollamaService;
            secondary = anthropicService;
        } else {
            log.info("llm: backend=none — every /v1/classify call will return LLM_NOT_CONFIGURED until a backend is wired");
            return new NotConfiguredLlmService();
        }

        if (primary == null) {
            log.warn("llm: backend={} but its ChatModel bean is missing — falling back to not-configured. (Set ANTHROPIC_API_KEY or spring.ai.ollama.base-url depending on the backend.)",
                    backend);
            return new NotConfiguredLlmService();
        }

        LlmService selected;
        if (fallbackEnabled && secondary != null) {
            log.info("llm: fallback enabled — primary={}, secondary={}",
                    primary.activeBackend(), secondary.activeBackend());
            MeterRegistry registry = meterRegistryProvider == null ? null : meterRegistryProvider.getIfAvailable();
            selected = new FallbackLlmService(primary, secondary, registry);
        } else {
            if (fallbackEnabled) {
                log.warn("llm: fallback enabled but secondary backend not configured — running primary only");
            }
            selected = primary;
        }

        return wrapWithMcpCap(selected, mcpAvailabilityProbeProvider,
                mcpCapEnabled, mcpCapMaxConfidence, meterRegistryProvider);
    }

    private static LlmService buildAnthropicIfAvailable(
            ObjectProvider<AnthropicChatModel> provider, PromptBlockResolver resolver,
            String model, double temperature, int maxTokens,
            ObjectMapper mapper, ToolCallbackProvider[] toolCallbacks,
            boolean circuitBreakerEnabled, int failureThreshold, java.time.Duration openCooldown,
            ObjectProvider<MeterRegistry> meterRegistryProvider) {
        AnthropicChatModel chatModel = provider.getIfAvailable();
        if (chatModel == null) return null;
        log.info("llm: backend=anthropic available model={} temperature={} maxTokens={}",
                model, temperature, maxTokens);
        LlmService base = new AnthropicLlmService(chatModel, resolver, model, temperature, maxTokens,
                mapper, toolCallbacks);
        return wrapWithCircuitBreaker(base, "anthropic",
                circuitBreakerEnabled, failureThreshold, openCooldown, meterRegistryProvider);
    }

    private static LlmService buildOllamaIfAvailable(
            ObjectProvider<OllamaChatModel> provider, PromptBlockResolver resolver,
            String model, double temperature, int numCtx,
            ObjectMapper mapper, ToolCallbackProvider[] toolCallbacks,
            boolean circuitBreakerEnabled, int failureThreshold, java.time.Duration openCooldown,
            ObjectProvider<MeterRegistry> meterRegistryProvider) {
        OllamaChatModel chatModel = provider.getIfAvailable();
        if (chatModel == null) return null;
        log.info("llm: backend=ollama available model={} temperature={} numCtx={}",
                model, temperature, numCtx);
        LlmService base = new OllamaLlmService(chatModel, resolver, model, temperature, numCtx,
                mapper, toolCallbacks);
        return wrapWithCircuitBreaker(base, "ollama",
                circuitBreakerEnabled, failureThreshold, openCooldown, meterRegistryProvider);
    }

    private static LlmService wrapWithCircuitBreaker(
            LlmService delegate, String backendName,
            boolean enabled, int failureThreshold, java.time.Duration openCooldown,
            ObjectProvider<MeterRegistry> meterRegistryProvider) {
        if (!enabled) {
            log.info("llm: circuit breaker disabled for backend={}", backendName);
            return delegate;
        }
        CircuitBreaker breaker = new CircuitBreaker(
                "llm-" + backendName, failureThreshold, openCooldown);
        log.info("llm: wrapped backend={} with circuit breaker (threshold={}, cooldown={})",
                backendName, failureThreshold, openCooldown);
        MeterRegistry registry = meterRegistryProvider == null ? null : meterRegistryProvider.getIfAvailable();
        return new CircuitBreakerLlmService(delegate, breaker, registry);
    }

    private static LlmService wrapWithMcpCap(
            LlmService delegate,
            ObjectProvider<McpAvailabilityProbe> probeProvider,
            boolean enabled, float maxConfidence,
            ObjectProvider<MeterRegistry> meterRegistryProvider) {
        if (!enabled) {
            log.info("llm: MCP confidence cap disabled");
            return delegate;
        }
        McpAvailabilityProbe probe = probeProvider.getIfAvailable();
        if (probe == null) {
            log.warn("llm: MCP confidence cap enabled but McpAvailabilityProbe bean missing — skipping wrap");
            return delegate;
        }
        log.info("llm: wrapped LLM service with MCP confidence cap (maxConfidence={})", maxConfidence);
        MeterRegistry registry = meterRegistryProvider == null ? null : meterRegistryProvider.getIfAvailable();
        return new McpCappingLlmService(delegate, probe, maxConfidence, registry);
    }
}
