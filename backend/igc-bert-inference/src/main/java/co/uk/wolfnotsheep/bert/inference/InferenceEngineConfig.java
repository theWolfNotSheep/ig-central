package co.uk.wolfnotsheep.bert.inference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the {@link InferenceEngine} bean. Default
 * {@code igc.bert.inference.engine=none} → {@link NotLoadedInferenceEngine}.
 * Setting the property to {@code djl} (Phase 1.4 follow-up) swaps in
 * the real DJL + ONNX Runtime impl.
 */
@Configuration
public class InferenceEngineConfig {

    private static final Logger log = LoggerFactory.getLogger(InferenceEngineConfig.class);

    @Bean
    @ConditionalOnMissingBean(InferenceEngine.class)
    public InferenceEngine inferenceEngine(
            @Value("${igc.bert.inference.engine:none}") String engine) {
        if (!"none".equalsIgnoreCase(engine)) {
            // Real DJL impl ships in a follow-up PR — until then, fall
            // through to the not-loaded engine but log loudly so the
            // operator notices the misconfiguration.
            log.warn("bert: igc.bert.inference.engine={} is requested but no implementation is wired in this build — falling back to not-loaded",
                    engine);
        }
        log.info("bert: engine=none — every /v1/infer call will return MODEL_NOT_LOADED until an artefact is wired");
        return new NotLoadedInferenceEngine();
    }
}
