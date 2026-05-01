package co.uk.wolfnotsheep.llm.config;

import co.uk.wolfnotsheep.platform.config.services.AppConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Factory for LLM calls that reads configuration at call-time rather than startup.
 *
 * <p>Resolution order for each parameter:
 * <ol>
 *   <li>Per-node overrides (from pipeline visual editor)</li>
 *   <li>Global config (from AI Settings page → MongoDB app_config)</li>
 *   <li>Environment variable defaults</li>
 *   <li>Hardcoded fallbacks</li>
 * </ol>
 */
@Service
public class LlmClientFactory {

    private static final Logger log = LoggerFactory.getLogger(LlmClientFactory.class);

    private final AnthropicChatModel anthropicModel;
    private final OllamaChatModel ollamaModel;
    private final ToolCallbackProvider[] toolCallbackProviders;
    private final AppConfigService configService;

    @Value("${llm.provider:anthropic}")
    private String envProvider;

    @Value("${spring.ai.anthropic.chat.options.model:claude-sonnet-4-20250514}")
    private String envAnthropicModel;

    @Value("${spring.ai.ollama.chat.model:qwen2.5:32b}")
    private String envOllamaModel;

    @Value("${spring.ai.anthropic.chat.options.temperature:0.1}")
    private double envTemperature;

    @Value("${spring.ai.anthropic.chat.options.max-tokens:4096}")
    private int envMaxTokens;

    public LlmClientFactory(AnthropicChatModel anthropicModel,
                             OllamaChatModel ollamaModel,
                             ToolCallbackProvider[] toolCallbackProviders,
                             AppConfigService configService) {
        this.anthropicModel = anthropicModel;
        this.ollamaModel = ollamaModel;
        this.toolCallbackProviders = toolCallbackProviders;
        this.configService = configService;
    }

    /**
     * Make an LLM call with MCP tools, using global config defaults.
     */
    public String call(String systemPrompt, String userPrompt) {
        return call(systemPrompt, userPrompt, Map.of());
    }

    /**
     * Make an LLM call with MCP tools, with per-node overrides.
     *
     * @param overrides node-level config (provider, model, temperature, maxTokens).
     *                  Empty/null values fall through to global config.
     */
    public String call(String systemPrompt, String userPrompt, Map<String, Object> overrides) {
        String provider = resolve("provider", overrides, "llm.provider", envProvider);
        boolean isOllama = "ollama".equalsIgnoreCase(provider);

        ChatModel model = isOllama ? ollamaModel : anthropicModel;
        ChatClient client = ChatClient.builder(model)
                .defaultToolCallbacks(toolCallbackProviders)
                .build();

        String modelId = resolveModel(provider, overrides);
        double temperature = resolveDouble("temperature", overrides, "pipeline.llm.temperature", envTemperature);
        int maxTokens = resolveInt("maxTokens", overrides, "pipeline.llm.max_tokens", envMaxTokens);

        log.info("LLM call: provider={}, model={}, temperature={}, maxTokens={}",
                provider, modelId, temperature, maxTokens);

        if (isOllama) {
            return client.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .options(OllamaChatOptions.builder()
                            .model(modelId)
                            .temperature(temperature)
                            .numCtx(resolveInt("numCtx", overrides, "llm.ollama.ctx_size", 32768)))
                    .call()
                    .content();
        } else {
            return client.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .options(AnthropicChatOptions.builder()
                            .model(modelId)
                            .temperature(temperature)
                            .maxTokens(maxTokens))
                    .call()
                    .content();
        }
    }

    /**
     * Make an LLM call WITHOUT MCP tools (custom prompt mode).
     */
    public String callWithoutTools(String systemPrompt, String userPrompt, Map<String, Object> overrides) {
        String provider = resolve("provider", overrides, "llm.provider", envProvider);
        boolean isOllama = "ollama".equalsIgnoreCase(provider);

        ChatModel model = isOllama ? ollamaModel : anthropicModel;
        ChatClient client = ChatClient.builder(model).build();

        String modelId = resolveModel(provider, overrides);
        double temperature = resolveDouble("temperature", overrides, "pipeline.llm.temperature", envTemperature);
        int maxTokens = resolveInt("maxTokens", overrides, "pipeline.llm.max_tokens", envMaxTokens);

        if (isOllama) {
            return client.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .options(OllamaChatOptions.builder()
                            .model(modelId)
                            .temperature(temperature)
                            .numCtx(resolveInt("numCtx", overrides, "llm.ollama.ctx_size", 32768)))
                    .call()
                    .content();
        } else {
            return client.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .options(AnthropicChatOptions.builder()
                            .model(modelId)
                            .temperature(temperature)
                            .maxTokens(maxTokens))
                    .call()
                    .content();
        }
    }

    /**
     * Get the currently active provider name (for logging/display).
     */
    public String getActiveProvider() {
        return configService.getValue("llm.provider", envProvider);
    }

    /**
     * Get the currently active model name (for logging/display).
     */
    public String getActiveModel() {
        return resolveModel(getActiveProvider(), Map.of());
    }

    // ── Resolution helpers ────────────────────────────────────────────

    private String resolveModel(String provider, Map<String, Object> overrides) {
        if ("ollama".equalsIgnoreCase(provider)) {
            return resolve("model", overrides, "llm.ollama.model", envOllamaModel);
        }
        return resolve("model", overrides, "llm.anthropic.model", envAnthropicModel);
    }

    /**
     * Resolve a string value: node override → AppConfigService → env default.
     */
    private String resolve(String key, Map<String, Object> overrides, String configKey, String envDefault) {
        if (overrides != null && overrides.containsKey(key)) {
            Object val = overrides.get(key);
            if (val != null && !val.toString().isBlank()) {
                return val.toString();
            }
        }
        return configService.getValue(configKey, envDefault);
    }

    private double resolveDouble(String key, Map<String, Object> overrides, String configKey, double envDefault) {
        if (overrides != null && overrides.containsKey(key)) {
            Object val = overrides.get(key);
            if (val instanceof Number n) return n.doubleValue();
            if (val instanceof String s && !s.isBlank()) {
                try { return Double.parseDouble(s); } catch (NumberFormatException ignored) {}
            }
        }
        return configService.getValue(configKey, envDefault);
    }

    private int resolveInt(String key, Map<String, Object> overrides, String configKey, int envDefault) {
        if (overrides != null && overrides.containsKey(key)) {
            Object val = overrides.get(key);
            if (val instanceof Number n) return n.intValue();
            if (val instanceof String s && !s.isBlank()) {
                try { return Integer.parseInt(s); } catch (NumberFormatException ignored) {}
            }
        }
        return configService.getValue(configKey, envDefault);
    }
}
