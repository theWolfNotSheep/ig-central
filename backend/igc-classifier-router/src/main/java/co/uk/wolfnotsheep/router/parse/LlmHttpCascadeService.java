package co.uk.wolfnotsheep.router.parse;

import io.micrometer.observation.annotation.Observed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * LLM tier of the cascade — HTTP variant. Replaces the legacy
 * Rabbit-driven {@link LlmDispatchCascadeService} when
 * {@code igc.router.cascade.llm-http.enabled=true}.
 *
 * <p>Unlike {@link BertOrchestratorCascadeService} and
 * {@link SlmOrchestratorCascadeService}, this service doesn't wrap
 * an inner cascade — the LLM tier IS the cascade's floor (CSV #1).
 * If the LLM HTTP dispatch fails, the failure surfaces to the caller
 * as {@code ROUTER_LLM_FAILED} (mirrors the legacy Rabbit failure
 * mapping).
 *
 * <p>{@link LlmBlockUnknownException} (HTTP 422 from the worker)
 * propagates to the controller for 422 mapping, same shape as the
 * BERT / SLM equivalents.
 */
public class LlmHttpCascadeService implements CascadeService {

    private static final Logger log = LoggerFactory.getLogger(LlmHttpCascadeService.class);

    private final LlmHttpDispatcher dispatcher;

    public LlmHttpCascadeService(LlmHttpDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Override
    @Observed(name = "cascade.run", contextualName = "cascade-run-llm-http",
            lowCardinalityKeyValues = {"component", "router", "tier", "llm-http"})
    public CascadeOutcome run(String blockId, Integer blockVersion, String blockType, String text) {
        long byteCount = text == null ? 0L : text.getBytes(StandardCharsets.UTF_8).length;
        long started = System.currentTimeMillis();

        LlmInferenceResult result;
        try {
            result = dispatcher.classify(blockId, blockVersion, /* nodeRunId */ null, text);
        } catch (LlmTierFallthroughException failure) {
            // The LLM tier is the floor — a fallthrough here means
            // we have nothing to fall back to. Surface as a failure
            // so the controller maps it to ROUTER_LLM_FAILED (502).
            long durationMs = System.currentTimeMillis() - started;
            log.warn("router: LLM HTTP tier failed errorCode={} after {}ms",
                    failure.errorCode(), durationMs);
            throw new LlmJobFailedException(
                    "LLM HTTP tier failed (" + failure.errorCode() + "): "
                            + failure.getMessage());
        }
        // LlmBlockUnknownException intentionally not caught — propagates
        // for 422 mapping.

        long durationMs = System.currentTimeMillis() - started;
        Map<String, Object> resultMap = new LinkedHashMap<>(result.result());
        resultMap.put("confidence", result.confidence());
        if (result.modelId() != null) resultMap.put("modelId", result.modelId());

        CascadeOutcome.TraceStep llmStep = new CascadeOutcome.TraceStep(
                "LLM", true, result.confidence(), durationMs, result.costUnits(), null);

        return new CascadeOutcome(
                "LLM",
                result.confidence(),
                resultMap,
                /* rationale */ null,
                /* evidence */ List.of(),
                /* trace */ List.of(llmStep),
                /* costUnits */ result.costUnits(),
                byteCount);
    }
}
