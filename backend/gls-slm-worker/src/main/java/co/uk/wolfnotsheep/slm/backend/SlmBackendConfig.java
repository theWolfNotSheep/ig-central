package co.uk.wolfnotsheep.slm.backend;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the {@link SlmService} bean. Default
 * {@code gls.slm.worker.backend=none} → {@link NotConfiguredSlmService}.
 * Setting the property to {@code anthropic} or {@code ollama} (Phase
 * 1.5 follow-up) swaps in a real backend.
 */
@Configuration
public class SlmBackendConfig {

    private static final Logger log = LoggerFactory.getLogger(SlmBackendConfig.class);

    @Bean
    @ConditionalOnMissingBean(SlmService.class)
    public SlmService slmService(
            @Value("${gls.slm.worker.backend:none}") String backend) {
        if (!"none".equalsIgnoreCase(backend)) {
            // Real backends ship in follow-up PRs — until then, fall
            // through to the not-configured stub but log loudly so
            // the operator notices the misconfiguration.
            log.warn("slm: gls.slm.worker.backend={} is requested but no implementation is wired in this build — falling back to not-configured",
                    backend);
        }
        log.info("slm: backend=none — every /v1/classify call will return SLM_NOT_CONFIGURED until a backend is wired");
        return new NotConfiguredSlmService();
    }
}
