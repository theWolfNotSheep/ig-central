package co.uk.wolfnotsheep.llmworker.backend;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Phase 2.2 PR4 — primary/secondary fallback inside the LLM worker.
 *
 * <p>When the primary backend fails (transport / 5xx / circuit-breaker
 * open), this decorator immediately tries the secondary. Caller-side
 * gates ({@link BudgetExceededException}, {@link RateLimitExceededException})
 * propagate without fallback — they reflect our own back-pressure, not
 * upstream health.
 *
 * <p>Wrapping order in {@link LlmBackendConfig}:
 * <pre>
 *   McpCapping( Fallback( CircuitBreaker(primaryBase), CircuitBreaker(secondaryBase) ) )
 * </pre>
 * Each backend has its own circuit breaker so a failing primary trips
 * its breaker first, after which the fallback path is the fast path.
 *
 * <p>Phase 2.6 — when a {@link MeterRegistry} is supplied, increments
 * {@code llm.fallback.invocations{primary, reason}} per fallback event.
 * Reason is one of: {@code circuit_breaker_open} (primary's breaker was
 * already OPEN at call time), {@code primary_failed} (primary threw a
 * non-gate exception during the call).
 */
public class FallbackLlmService implements LlmService {

    private static final Logger log = LoggerFactory.getLogger(FallbackLlmService.class);

    private final LlmService primary;
    private final LlmService secondary;
    private final MeterRegistry meterRegistry;

    public FallbackLlmService(LlmService primary, LlmService secondary) {
        this(primary, secondary, null);
    }

    public FallbackLlmService(LlmService primary, LlmService secondary, MeterRegistry meterRegistry) {
        this.primary = primary;
        this.secondary = secondary;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public LlmResult classify(String blockId, Integer blockVersion, String text) {
        try {
            return primary.classify(blockId, blockVersion, text);
        } catch (BudgetExceededException | RateLimitExceededException e) {
            // Caller-side gates — never fall back, the caller must back off.
            throw e;
        } catch (CircuitBreakerOpenException e) {
            // Primary's breaker is open — fast-fall to secondary.
            log.debug("primary {} breaker open — falling back to {}",
                    primary.activeBackend(), secondary.activeBackend());
            recordFallback("circuit_breaker_open");
            return secondary.classify(blockId, blockVersion, text);
        } catch (RuntimeException e) {
            // Transport / 5xx / unexpected — try secondary once.
            log.warn("primary {} failed ({}) — falling back to {}",
                    primary.activeBackend(), e.getMessage(), secondary.activeBackend());
            recordFallback("primary_failed");
            return secondary.classify(blockId, blockVersion, text);
        }
    }

    @Override
    public LlmBackendId activeBackend() {
        // The configured primary, even when a given call landed on
        // secondary — the fallback is transparent at the response level.
        return primary.activeBackend();
    }

    @Override
    public boolean isReady() {
        return primary.isReady() || secondary.isReady();
    }

    /** Visible for testing + observability. */
    public LlmService primary() { return primary; }
    /** Visible for testing + observability. */
    public LlmService secondary() { return secondary; }

    private void recordFallback(String reason) {
        if (meterRegistry == null) return;
        Counter.builder("llm.fallback.invocations")
                .description("Count of LLM fallback invocations from primary to secondary backend")
                .tags(Tags.of(
                        "primary", primary.activeBackend().name().toLowerCase(),
                        "reason", reason))
                .register(meterRegistry)
                .increment();
    }
}
