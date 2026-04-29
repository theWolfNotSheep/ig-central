package co.uk.wolfnotsheep.llmworker.backend;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the {@link LlmService} bean. Phase 1.6 PR1 ships only the
 * not-configured stub — every {@code /v1/classify} call returns 503
 * {@code LLM_NOT_CONFIGURED}. PR2 lifts the existing
 * {@code gls-llm-orchestration} Anthropic + MCP integration into a
 * real backend behind this same interface.
 */
@Configuration
public class LlmBackendConfig {

    private static final Logger log = LoggerFactory.getLogger(LlmBackendConfig.class);

    @Bean
    @ConditionalOnMissingBean(LlmService.class)
    public LlmService llmService(
            @Value("${gls.llm.worker.backend:none}") String backend) {
        if (!"none".equalsIgnoreCase(backend)) {
            log.warn("llm: gls.llm.worker.backend={} is requested but no real backend is wired in this build (PR2 follow-up) — falling back to not-configured",
                    backend);
        }
        log.info("llm: backend=none — every /v1/classify call will return LLM_NOT_CONFIGURED until PR2 lands");
        return new NotConfiguredLlmService();
    }
}
