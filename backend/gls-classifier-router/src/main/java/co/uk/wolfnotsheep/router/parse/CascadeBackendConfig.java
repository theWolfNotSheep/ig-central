package co.uk.wolfnotsheep.router.parse;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the {@link CascadeService} bean. Default is the deterministic
 * mock; flipping {@code gls.router.cascade.llm.enabled=true} causes
 * {@link RouterRabbitMqConfig} to register an
 * {@link LlmDispatchCascadeService} which takes precedence by name
 * over this fallback.
 */
@Configuration
public class CascadeBackendConfig {

    @Bean
    @ConditionalOnMissingBean(CascadeService.class)
    public CascadeService cascadeService() {
        return new MockCascadeService();
    }
}
