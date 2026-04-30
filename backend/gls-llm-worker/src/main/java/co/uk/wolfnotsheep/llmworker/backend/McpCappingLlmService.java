package co.uk.wolfnotsheep.llmworker.backend;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Decorator that caps an {@link LlmService}'s reported confidence when
 * the MCP server is unreachable. Per architecture §6.6: workers should
 * proceed (no MCP tools = the LLM falls back to its baseline reasoning),
 * but downstream consumers need a signal that the result is less
 * trustworthy than usual. Capping confidence is that signal.
 *
 * <p>Cap behaviour: if {@link McpAvailabilityProbe#isAvailable()} is
 * {@code false} at return time, and the underlying result's confidence
 * exceeds {@code maxConfidence}, the confidence is replaced with
 * {@code maxConfidence}; rationale is suffixed with a note. All other
 * fields pass through unchanged.
 *
 * <p>If MCP isn't configured ({@link McpAvailabilityProbe#isConfigured()}
 * returns false), the cap never fires — workers running without MCP at
 * all aren't degraded relative to themselves.
 *
 * <p>Phase 2.6 — when a {@link MeterRegistry} is provided, increments
 * {@code llm.mcp.confidence_capped{backend}} per cap event. The
 * counter is registered lazily on first cap, tagged by the LlmResult's
 * backend so multi-backend deployments report distinct cap rates.
 */
public class McpCappingLlmService implements LlmService {

    private static final Logger log = LoggerFactory.getLogger(McpCappingLlmService.class);
    private static final String CAP_NOTE_PREFIX = " [confidence capped: MCP unreachable]";
    private static final String COUNTER_NAME = "llm.mcp.confidence_capped";

    private final LlmService delegate;
    private final McpAvailabilityProbe probe;
    private final float maxConfidence;
    private final MeterRegistry meterRegistry;

    public McpCappingLlmService(
            LlmService delegate,
            McpAvailabilityProbe probe,
            float maxConfidence) {
        this(delegate, probe, maxConfidence, null);
    }

    public McpCappingLlmService(
            LlmService delegate,
            McpAvailabilityProbe probe,
            float maxConfidence,
            MeterRegistry meterRegistry) {
        if (maxConfidence < 0f || maxConfidence > 1f) {
            throw new IllegalArgumentException("maxConfidence must be within [0, 1]");
        }
        this.delegate = delegate;
        this.probe = probe;
        this.maxConfidence = maxConfidence;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public LlmResult classify(String blockId, Integer blockVersion, String text) {
        LlmResult result = delegate.classify(blockId, blockVersion, text);
        if (!probe.isConfigured() || probe.isAvailable()) {
            return result;
        }
        if (result == null || result.confidence() <= maxConfidence) {
            return result;
        }
        log.debug("MCP unreachable — capping confidence from {} to {} for backend {}",
                result.confidence(), maxConfidence, result.backend());
        recordCap(result.backend());
        String rationale = result.rationale() == null ? "" : result.rationale();
        return new LlmResult(
                result.result(),
                maxConfidence,
                rationale + CAP_NOTE_PREFIX,
                result.backend(),
                result.modelId(),
                result.tokensIn(),
                result.tokensOut());
    }

    private void recordCap(LlmBackendId backend) {
        if (meterRegistry == null) return;
        String tag = backend == null ? "unknown" : backend.name().toLowerCase();
        Counter.builder(COUNTER_NAME)
                .description("Count of LLM responses whose confidence was capped due to MCP being unreachable")
                .tags(Tags.of("backend", tag))
                .register(meterRegistry)
                .increment();
    }

    @Override
    public LlmBackendId activeBackend() {
        return delegate.activeBackend();
    }

    @Override
    public boolean isReady() {
        return delegate.isReady();
    }

    /** Visible for testing. */
    LlmService delegate() { return delegate; }
    /** Visible for testing. */
    float maxConfidence() { return maxConfidence; }
}
