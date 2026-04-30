package co.uk.wolfnotsheep.router.web;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

@Component
public class ExtractMetrics {

    private static final String DURATION = "gls_router_classify_duration_seconds";
    private static final String RESULT = "gls_router_classify_result_total";
    private static final String TIER_BY_CATEGORY = "gls_router_classify_tier_by_category_total";
    private static final String COST_UNITS = "gls_router_classify_cost_units_total";

    private final MeterRegistry registry;

    public ExtractMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public Timer.Sample startTimer() {
        return Timer.start(registry);
    }

    public void recordSuccess(Timer.Sample sample, String tierOfDecision) {
        Tags tags = Tags.of(
                Tag.of("outcome", "success"),
                Tag.of("tier", safe(tierOfDecision)));
        sample.stop(registry.timer(DURATION, tags));
        registry.counter(RESULT, tags).increment();
    }

    public void recordFailure(Timer.Sample sample, String errorCode) {
        Tags tags = Tags.of(
                Tag.of("outcome", "failure"),
                Tag.of("error_code", errorCode));
        sample.stop(registry.timer(DURATION, tags));
        registry.counter(RESULT, tags).increment();
    }

    public void recordIdempotencyShortCircuit(String outcome) {
        Tags tags = Tags.of(Tag.of("outcome", outcome));
        registry.counter(RESULT, tags).increment();
    }

    /**
     * Phase 2.6 plan item 1 — tier-of-decision per category. Counter
     * tagged by {@code tier} (BERT / SLM / LLM / ROUTER_SHORT_CIRCUIT /
     * MOCK) and {@code category} (extracted from the cascade result
     * map; {@code "unknown"} when absent).
     *
     * <p>Cardinality note: {@code category} is bounded by the org's
     * taxonomy size — typically tens to hundreds. Tagging per category
     * creates O(tiers × categories) time series; acceptable for
     * typical Prometheus scales.
     */
    public void recordTierByCategory(String tierOfDecision, String category) {
        Tags tags = Tags.of(
                Tag.of("tier", safe(tierOfDecision)),
                Tag.of("category", safe(category)));
        registry.counter(TIER_BY_CATEGORY, tags).increment();
    }

    /**
     * Phase 2.6 plan item 3 — cost units per tier. Counter
     * incremented by {@code costUnits} per call, tagged by
     * {@code tier}. Operators derive cost-per-document (mean) and
     * cost-per-tier (sum) in the TSDB; daily-spend total is the
     * windowed sum.
     */
    public void recordCost(String tierOfDecision, long costUnits) {
        if (costUnits <= 0L) return;
        Tags tags = Tags.of(Tag.of("tier", safe(tierOfDecision)));
        registry.counter(COST_UNITS, tags).increment(costUnits);
    }

    private static String safe(String s) {
        return s == null || s.isBlank() ? "unknown" : s.toLowerCase();
    }
}
