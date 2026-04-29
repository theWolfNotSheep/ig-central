package co.uk.wolfnotsheep.router.parse;

import io.micrometer.observation.annotation.Observed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Cascade orchestrator that fronts an inner {@link CascadeService}
 * with the SLM tier. For {@code PROMPT} blocks: dispatches to
 * {@code gls-slm-worker}; on success returns the SLM outcome, on
 * fallthrough escalates to the inner cascade.
 *
 * <p>For {@code BERT_CLASSIFIER} blocks the SLM tier is bypassed —
 * the SLM worker only handles PROMPT classifications. (BERT_CLASSIFIER
 * blocks have already been served by the BERT tier upstream of this
 * orchestrator, or fell through to here without a meaningful SLM
 * shape — either way, delegate to the inner cascade.)
 *
 * <p>Compose this with {@link BertOrchestratorCascadeService} to get
 * the full cascade: BERT → SLM → inner (LLM/mock). Each tier wraps
 * the next, and each is independently activated by its own feature
 * flag, so the cascade can be exercised one tier at a time.
 */
public class SlmOrchestratorCascadeService implements CascadeService {

    private static final Logger log = LoggerFactory.getLogger(SlmOrchestratorCascadeService.class);

    private final SlmHttpDispatcher dispatcher;
    private final CascadeService inner;
    private final Supplier<RouterPolicy> policy;

    public SlmOrchestratorCascadeService(SlmHttpDispatcher dispatcher, CascadeService inner) {
        this(dispatcher, inner, () -> RouterPolicy.DEFAULT);
    }

    public SlmOrchestratorCascadeService(
            SlmHttpDispatcher dispatcher,
            CascadeService inner,
            Supplier<RouterPolicy> policy) {
        this.dispatcher = dispatcher;
        this.inner = inner;
        this.policy = policy;
    }

    @Override
    @Observed(name = "cascade.run", contextualName = "cascade-run-slm",
            lowCardinalityKeyValues = {"component", "router", "tier", "slm"})
    public CascadeOutcome run(String blockId, Integer blockVersion, String blockType, String text) {
        if (!"PROMPT".equalsIgnoreCase(blockType)) {
            return inner.run(blockId, blockVersion, blockType, text);
        }

        RouterPolicy.TierPolicy slmPolicy = policy.get().slm();
        long byteCount = text == null ? 0L : text.getBytes(StandardCharsets.UTF_8).length;

        if (!slmPolicy.enabled()) {
            CascadeOutcome.TraceStep skipStep = new CascadeOutcome.TraceStep(
                    "SLM", false, null, 0L, 0L, "TIER_DISABLED");
            CascadeOutcome innerOutcome = inner.run(blockId, blockVersion, blockType, text);
            return prependTrace(innerOutcome, skipStep, byteCount);
        }

        long started = System.currentTimeMillis();

        SlmInferenceResult result;
        try {
            result = dispatcher.classify(blockId, blockVersion, /* nodeRunId */ null, text);
        } catch (SlmTierFallthroughException fallthrough) {
            long durationMs = System.currentTimeMillis() - started;
            log.debug("router: SLM fallthrough errorCode={} after {}ms — escalating",
                    fallthrough.errorCode(), durationMs);
            CascadeOutcome.TraceStep slmStep = new CascadeOutcome.TraceStep(
                    "SLM", false, null, durationMs, byteCount / 1024, fallthrough.errorCode());
            CascadeOutcome innerOutcome = inner.run(blockId, blockVersion, blockType, text);
            return prependTrace(innerOutcome, slmStep, byteCount);
        }
        // SlmBlockUnknownException intentionally not caught — propagates
        // to the controller where it maps to 422.

        long durationMs = System.currentTimeMillis() - started;

        if (result.confidence() < slmPolicy.accept()) {
            log.debug("router: SLM confidence {} below threshold {} — escalating",
                    result.confidence(), slmPolicy.accept());
            CascadeOutcome.TraceStep slmStep = new CascadeOutcome.TraceStep(
                    "SLM", false, result.confidence(), durationMs,
                    result.costUnits(), "BELOW_THRESHOLD");
            CascadeOutcome innerOutcome = inner.run(blockId, blockVersion, blockType, text);
            return prependTrace(innerOutcome, slmStep, byteCount);
        }

        Map<String, Object> resultMap = new LinkedHashMap<>(result.result());
        resultMap.put("confidence", result.confidence());
        if (result.modelId() != null) resultMap.put("modelId", result.modelId());
        if (result.backend() != null) resultMap.put("backend", result.backend());

        CascadeOutcome.TraceStep slmStep = new CascadeOutcome.TraceStep(
                "SLM", true, result.confidence(), durationMs, result.costUnits(), null);

        return new CascadeOutcome(
                "SLM",
                result.confidence(),
                resultMap,
                /* rationale */ null,
                /* evidence */ List.of(),
                /* trace */ List.of(slmStep),
                /* costUnits */ result.costUnits(),
                byteCount);
    }

    private static CascadeOutcome prependTrace(
            CascadeOutcome innerOutcome, CascadeOutcome.TraceStep slmStep, long byteCount) {
        List<CascadeOutcome.TraceStep> combined = new ArrayList<>();
        combined.add(slmStep);
        if (innerOutcome.trace() != null) combined.addAll(innerOutcome.trace());
        long combinedCost = (slmStep.costUnits() == null ? 0L : slmStep.costUnits())
                + innerOutcome.costUnits();
        long combinedBytes = Math.max(byteCount, innerOutcome.byteCount());
        return new CascadeOutcome(
                innerOutcome.tierOfDecision(),
                innerOutcome.confidence(),
                innerOutcome.result(),
                innerOutcome.rationale(),
                innerOutcome.evidence(),
                combined,
                combinedCost,
                combinedBytes);
    }
}
