package co.uk.wolfnotsheep.router.parse;

import io.micrometer.observation.annotation.Observed;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Phase 1.2 mock. Returns a deterministic block-shaped result so the
 * orchestrator + admin UI can integrate against the contract while
 * the real cascade fills in.
 *
 * <p>Result shape varies by declared block type:
 *
 * <ul>
 *     <li>{@code BERT_CLASSIFIER} → {@code { label: "MOCK_LABEL",
 *         confidence: 0.5 }}.</li>
 *     <li>{@code PROMPT} (default) → {@code { category: "MOCK_CATEGORY",
 *         sensitivity: "INTERNAL", confidence: 0.5 }}.</li>
 * </ul>
 *
 * <p>{@code tierOfDecision = "MOCK"} on every call so observers can
 * see the cascade isn't real yet.
 */
@Service
public class MockCascadeService implements CascadeService {

    private static final float MOCK_CONFIDENCE = 0.5f;

    @Override
    @Observed(name = "cascade.run", contextualName = "cascade-run",
            lowCardinalityKeyValues = {"component", "router", "tier", "mock"})
    public CascadeOutcome run(String blockId, Integer blockVersion, String blockType, String text) {
        Map<String, Object> result = new LinkedHashMap<>();
        if ("BERT_CLASSIFIER".equalsIgnoreCase(blockType)) {
            result.put("label", "MOCK_LABEL");
            result.put("confidence", MOCK_CONFIDENCE);
        } else {
            result.put("category", "MOCK_CATEGORY");
            result.put("sensitivity", "INTERNAL");
            result.put("confidence", MOCK_CONFIDENCE);
        }
        long byteCount = text == null ? 0L : text.getBytes(StandardCharsets.UTF_8).length;
        return new CascadeOutcome(
                "MOCK",
                MOCK_CONFIDENCE,
                result,
                /* rationale */ "Phase 1.2 deterministic stub — real cascade lands in 1.4+.",
                /* evidence */ List.of(),
                /* trace */ List.of(new CascadeOutcome.TraceStep(
                        "BERT", false, 0.0f, 0L, 0L, "MOCK_DISABLED")),
                /* costUnits */ 0L,
                byteCount);
    }
}
