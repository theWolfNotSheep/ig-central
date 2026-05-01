package co.uk.wolfnotsheep.llm.config;

import co.uk.wolfnotsheep.platform.config.services.AppConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Configures the ChatClient based on the LLM provider setting.
 *
 * <p>Provider is determined by (in priority order):
 * <ol>
 *   <li>app_config collection key: llm.provider</li>
 *   <li>Environment variable: LLM_PROVIDER</li>
 *   <li>Default: anthropic</li>
 * </ol>
 *
 * <p>This allows admins to switch provider in the Settings UI without
 * changing environment variables (requires LLM worker restart).
 */
@Configuration
public class LlmProviderConfig {

    private static final Logger log = LoggerFactory.getLogger(LlmProviderConfig.class);

    @Value("${llm.provider:anthropic}")
    private String envProvider;

    @Bean
    @Primary
    public ChatClient chatClient(
            AnthropicChatModel anthropicModel,
            OllamaChatModel ollamaModel,
            ToolCallbackProvider[] toolCallbackProviders,
            AppConfigService configService) {

        // Check MongoDB config first, fall back to env var
        String provider = configService.getValue("llm.provider", envProvider);

        ChatModel model;
        if ("ollama".equalsIgnoreCase(provider)) {
            log.info("LLM provider: OLLAMA — model: {}", configService.getValue("llm.ollama.model", "default"));
            model = ollamaModel;
        } else {
            log.info("LLM provider: ANTHROPIC — model: {}", configService.getValue("llm.anthropic.model", "default"));
            model = anthropicModel;
        }

        return ChatClient.builder(model)
                .defaultToolCallbacks(toolCallbackProviders)
                .build();
    }
}
