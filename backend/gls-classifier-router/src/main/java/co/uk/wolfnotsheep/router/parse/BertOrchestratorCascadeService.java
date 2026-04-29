package co.uk.wolfnotsheep.router.parse;

import io.micrometer.observation.annotation.Observed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Cascade orchestrator that fronts an inner {@link CascadeService}
 * with the BERT tier. For {@code BERT_CLASSIFIER} blocks, dispatches
 * to {@code gls-bert-inference}; on success returns the BERT outcome,
 * on fallthrough escalates to the inner cascade.
 *
 * <p>For other block types ({@code PROMPT}) the BERT tier is bypassed
 * — BERT can't handle open-ended classification without the
 * {@code labelMapping} from a {@code BERT_CLASSIFIER} block, and
 * resolving that block from PROMPT context is deferred to the next
 * Phase 1.4 PR. PROMPT requests delegate to {@link #inner} directly.
 *
 * <p>The orchestrator never reads the ROUTER block from Mongo (per
 * the Phase 1.2 close-off note: tier-aware threshold dispatch lands
 * with the BERT_CLASSIFIER block resolution PR). For now BERT 200
 * responses are accepted as-is — when the trainer publishes its
 * first artefact and the operator enables BERT, the cascade will
 * route to BERT for matching block types until a tier threshold
 * gates it.
 */
public class BertOrchestratorCascadeService implements CascadeService {

    private static final Logger log = LoggerFactory.getLogger(BertOrchestratorCascadeService.class);

    private final BertHttpDispatcher dispatcher;
    private final CascadeService inner;

    public BertOrchestratorCascadeService(BertHttpDispatcher dispatcher, CascadeService inner) {
        this.dispatcher = dispatcher;
        this.inner = inner;
    }

    @Override
    @Observed(name = "cascade.run", contextualName = "cascade-run-bert",
            lowCardinalityKeyValues = {"component", "router", "tier", "bert"})
    public CascadeOutcome run(String blockId, Integer blockVersion, String blockType, String text) {
        if (!"BERT_CLASSIFIER".equalsIgnoreCase(blockType)) {
            return inner.run(blockId, blockVersion, blockType, text);
        }

        long byteCount = text == null ? 0L : text.getBytes(StandardCharsets.UTF_8).length;
        long started = System.currentTimeMillis();

        BertInferenceResult result;
        try {
            result = dispatcher.infer(blockId, blockVersion, /* nodeRunId */ null, text);
        } catch (BertTierFallthroughException fallthrough) {
            long durationMs = System.currentTimeMillis() - started;
            log.debug("router: BERT fallthrough errorCode={} after {}ms — escalating",
                    fallthrough.errorCode(), durationMs);
            CascadeOutcome.TraceStep bertStep = new CascadeOutcome.TraceStep(
                    "BERT", false, null, durationMs, byteCount / 1024, fallthrough.errorCode());
            CascadeOutcome innerOutcome = inner.run(blockId, blockVersion, blockType, text);
            return prependTrace(innerOutcome, bertStep, byteCount);
        }
        // BertBlockUnknownException intentionally not caught — propagates
        // to the controller where it maps to 422.

        long durationMs = System.currentTimeMillis() - started;
        Map<String, Object> resultMap = new LinkedHashMap<>();
        resultMap.put("label", result.label());
        resultMap.put("confidence", result.confidence());
        if (result.modelVersion() != null) resultMap.put("modelVersion", result.modelVersion());

        CascadeOutcome.TraceStep bertStep = new CascadeOutcome.TraceStep(
                "BERT", true, result.confidence(), durationMs, byteCount / 1024, null);

        return new CascadeOutcome(
                "BERT",
                result.confidence(),
                resultMap,
                /* rationale */ null,
                /* evidence */ List.of(),
                /* trace */ List.of(bertStep),
                /* costUnits */ Math.max(0L, byteCount / 1024),
                byteCount);
    }

    private static CascadeOutcome prependTrace(
            CascadeOutcome innerOutcome, CascadeOutcome.TraceStep bertStep, long byteCount) {
        List<CascadeOutcome.TraceStep> combined = new ArrayList<>();
        combined.add(bertStep);
        if (innerOutcome.trace() != null) combined.addAll(innerOutcome.trace());
        long combinedCost = (bertStep.costUnits() == null ? 0L : bertStep.costUnits())
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
