package co.uk.wolfnotsheep.slm.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.anthropic.AnthropicChatModel;
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
 *         is set) →
 *         {@link AnthropicHaikuSlmService}.</li>
 *     <li>{@code gls.slm.worker.backend=ollama} (Phase 1.5 PR3) →
 *         the Ollama backend.</li>
 *     <li>Default / misconfigured → {@link NotConfiguredSlmService}.</li>
 * </ol>
 *
 * <p>The Anthropic SDK's auto-config means the
 * {@link AnthropicChatModel} bean simply isn't created when the API
 * key is absent. We use {@link ObjectProvider#getIfAvailable} so the
 * service starts cleanly without one + falls through to the
 * not-configured stub.
 */
@Configuration
public class SlmBackendConfig {

    private static final Logger log = LoggerFactory.getLogger(SlmBackendConfig.class);

    @Bean
    @ConditionalOnMissingBean(SlmService.class)
    public SlmService slmService(
            @Value("${gls.slm.worker.backend:none}") String backend,
            ObjectProvider<AnthropicChatModel> anthropicProvider,
            ObjectProvider<PromptBlockResolver> blockResolverProvider,
            @Value("${gls.slm.worker.anthropic.model:claude-haiku-4-5}") String anthropicModel,
            @Value("${gls.slm.worker.anthropic.temperature:0.1}") double anthropicTemperature,
            @Value("${gls.slm.worker.anthropic.max-tokens:1024}") int anthropicMaxTokens,
            ObjectProvider<ObjectMapper> mapperProvider) {

        if ("anthropic".equalsIgnoreCase(backend)) {
            AnthropicChatModel model = anthropicProvider.getIfAvailable();
            PromptBlockResolver resolver = blockResolverProvider.getIfAvailable();
            if (model == null) {
                log.warn("slm: gls.slm.worker.backend=anthropic but AnthropicChatModel bean is missing — set ANTHROPIC_API_KEY. Falling back to not-configured.");
                return new NotConfiguredSlmService();
            }
            if (resolver == null) {
                log.warn("slm: PromptBlockResolver bean is missing — Mongo not wired? Falling back to not-configured.");
                return new NotConfiguredSlmService();
            }
            ObjectMapper mapper = mapperProvider.getIfAvailable(ObjectMapper::new);
            log.info("slm: backend=anthropic model={} temperature={} maxTokens={}",
                    anthropicModel, anthropicTemperature, anthropicMaxTokens);
            return new AnthropicHaikuSlmService(
                    model, resolver, anthropicModel, anthropicTemperature, anthropicMaxTokens, mapper);
        }

        if ("ollama".equalsIgnoreCase(backend)) {
            log.warn("slm: gls.slm.worker.backend=ollama is requested but the Ollama backend lands in Phase 1.5 PR3 — falling back to not-configured");
            return new NotConfiguredSlmService();
        }

        log.info("slm: backend=none — every /v1/classify call will return SLM_NOT_CONFIGURED until a backend is wired");
        return new NotConfiguredSlmService();
    }
}
