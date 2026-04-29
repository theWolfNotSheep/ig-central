package co.uk.wolfnotsheep.slm.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the {@link SlmService} bean. Selection order:
 *
 * <ol>
 *     <li>{@code gls.slm.worker.backend=anthropic} +
 *         {@link AnthropicChatModel} bean is present (Spring AI's
 *         starter autoconfigures it when {@code ANTHROPIC_API_KEY}
 *         is set) → {@link AnthropicHaikuSlmService}.</li>
 *     <li>{@code gls.slm.worker.backend=ollama} +
 *         {@link OllamaChatModel} bean is present (Spring AI's
 *         starter autoconfigures it when {@code spring.ai.ollama.base-url}
 *         is reachable) → {@link OllamaSlmService}.</li>
 *     <li>Default / misconfigured → {@link NotConfiguredSlmService}.</li>
 * </ol>
 *
 * <p>The Spring AI starters' auto-config means each ChatModel bean
 * simply isn't created when its required config is absent. We use
 * {@link ObjectProvider#getIfAvailable} for both, so the service
 * starts cleanly with neither, one, or both backends configured;
 * the {@code backend} property selects which one is active.
 */
@Configuration
public class SlmBackendConfig {

    private static final Logger log = LoggerFactory.getLogger(SlmBackendConfig.class);

    @Bean
    @ConditionalOnMissingBean(SlmService.class)
    public SlmService slmService(
            @Value("${gls.slm.worker.backend:none}") String backend,
            ObjectProvider<AnthropicChatModel> anthropicProvider,
            ObjectProvider<OllamaChatModel> ollamaProvider,
            ObjectProvider<PromptBlockResolver> blockResolverProvider,
            @Value("${gls.slm.worker.anthropic.model:claude-haiku-4-5}") String anthropicModel,
            @Value("${gls.slm.worker.anthropic.temperature:0.1}") double anthropicTemperature,
            @Value("${gls.slm.worker.anthropic.max-tokens:1024}") int anthropicMaxTokens,
            @Value("${gls.slm.worker.ollama.model:llama3.1:8b}") String ollamaModel,
            @Value("${gls.slm.worker.ollama.temperature:0.1}") double ollamaTemperature,
            @Value("${gls.slm.worker.ollama.num-ctx:32768}") int ollamaNumCtx,
            ObjectProvider<ObjectMapper> mapperProvider) {

        ObjectMapper mapper = mapperProvider.getIfAvailable(ObjectMapper::new);
        PromptBlockResolver resolver = blockResolverProvider.getIfAvailable();

        if ("anthropic".equalsIgnoreCase(backend)) {
            AnthropicChatModel model = anthropicProvider.getIfAvailable();
            if (model == null) {
                log.warn("slm: gls.slm.worker.backend=anthropic but AnthropicChatModel bean is missing — set ANTHROPIC_API_KEY. Falling back to not-configured.");
                return new NotConfiguredSlmService();
            }
            if (resolver == null) {
                log.warn("slm: PromptBlockResolver bean is missing — Mongo not wired? Falling back to not-configured.");
                return new NotConfiguredSlmService();
            }
            log.info("slm: backend=anthropic model={} temperature={} maxTokens={}",
                    anthropicModel, anthropicTemperature, anthropicMaxTokens);
            return new AnthropicHaikuSlmService(
                    model, resolver, anthropicModel, anthropicTemperature, anthropicMaxTokens, mapper);
        }

        if ("ollama".equalsIgnoreCase(backend)) {
            OllamaChatModel model = ollamaProvider.getIfAvailable();
            if (model == null) {
                log.warn("slm: gls.slm.worker.backend=ollama but OllamaChatModel bean is missing — set spring.ai.ollama.base-url to a reachable Ollama instance. Falling back to not-configured.");
                return new NotConfiguredSlmService();
            }
            if (resolver == null) {
                log.warn("slm: PromptBlockResolver bean is missing — Mongo not wired? Falling back to not-configured.");
                return new NotConfiguredSlmService();
            }
            log.info("slm: backend=ollama model={} temperature={} numCtx={}",
                    ollamaModel, ollamaTemperature, ollamaNumCtx);
            return new OllamaSlmService(
                    model, resolver, ollamaModel, ollamaTemperature, ollamaNumCtx, mapper);
        }

        log.info("slm: backend=none — every /v1/classify call will return SLM_NOT_CONFIGURED until a backend is wired");
        return new NotConfiguredSlmService();
    }
}
