package co.uk.wolfnotsheep.llmworker.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
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
 *     <li>{@code gls.llm.worker.backend=anthropic} +
 *         {@link AnthropicChatModel} bean is present (Spring AI's
 *         starter autoconfigures it when {@code ANTHROPIC_API_KEY}
 *         is set) → {@link AnthropicLlmService}.</li>
 *     <li>{@code gls.llm.worker.backend=ollama} +
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
            @Value("${gls.llm.worker.backend:none}") String backend,
            ObjectProvider<AnthropicChatModel> anthropicProvider,
            ObjectProvider<OllamaChatModel> ollamaProvider,
            ObjectProvider<PromptBlockResolver> blockResolverProvider,
            ObjectProvider<ToolCallbackProvider> toolCallbackProviderProvider,
            @Value("${gls.llm.worker.anthropic.model:claude-sonnet-4-5}") String anthropicModel,
            @Value("${gls.llm.worker.anthropic.temperature:0.1}") double anthropicTemperature,
            @Value("${gls.llm.worker.anthropic.max-tokens:4096}") int anthropicMaxTokens,
            @Value("${gls.llm.worker.ollama.model:qwen2.5:32b}") String ollamaModel,
            @Value("${gls.llm.worker.ollama.temperature:0.1}") double ollamaTemperature,
            @Value("${gls.llm.worker.ollama.num-ctx:32768}") int ollamaNumCtx,
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

        if ("anthropic".equalsIgnoreCase(backend)) {
            AnthropicChatModel model = anthropicProvider.getIfAvailable();
            if (model == null) {
                log.warn("llm: gls.llm.worker.backend=anthropic but AnthropicChatModel bean is missing — set ANTHROPIC_API_KEY. Falling back to not-configured.");
                return new NotConfiguredLlmService();
            }
            if (resolver == null) {
                log.warn("llm: PromptBlockResolver bean is missing — Mongo not wired? Falling back to not-configured.");
                return new NotConfiguredLlmService();
            }
            log.info("llm: backend=anthropic model={} temperature={} maxTokens={}",
                    anthropicModel, anthropicTemperature, anthropicMaxTokens);
            return new AnthropicLlmService(
                    model, resolver, anthropicModel, anthropicTemperature, anthropicMaxTokens,
                    mapper, toolCallbacks);
        }

        if ("ollama".equalsIgnoreCase(backend)) {
            OllamaChatModel model = ollamaProvider.getIfAvailable();
            if (model == null) {
                log.warn("llm: gls.llm.worker.backend=ollama but OllamaChatModel bean is missing — set spring.ai.ollama.base-url. Falling back to not-configured.");
                return new NotConfiguredLlmService();
            }
            if (resolver == null) {
                log.warn("llm: PromptBlockResolver bean is missing — Mongo not wired? Falling back to not-configured.");
                return new NotConfiguredLlmService();
            }
            log.info("llm: backend=ollama model={} temperature={} numCtx={}",
                    ollamaModel, ollamaTemperature, ollamaNumCtx);
            return new OllamaLlmService(
                    model, resolver, ollamaModel, ollamaTemperature, ollamaNumCtx, mapper,
                    toolCallbacks);
        }

        log.info("llm: backend=none — every /v1/classify call will return LLM_NOT_CONFIGURED until a backend is wired");
        return new NotConfiguredLlmService();
    }
}
