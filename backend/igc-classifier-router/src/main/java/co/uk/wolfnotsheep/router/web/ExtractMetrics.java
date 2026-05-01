package co.uk.wolfnotsheep.router.web;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

@Component
public class ExtractMetrics {

    private static final String DURATION = "igc_router_classify_duration_seconds";
    private static final String RESULT = "igc_router_classify_result_total";
    private static final String TIER_BY_CATEGORY = "igc_router_classify_tier_by_category_total";
    private static final String COST_UNITS = "igc_router_classify_cost_units_total";
    private static final String CASCADE_STEPS = "igc_router_classify_cascade_steps";
    private static final String TIER_STEP_DURATION = "igc_router_classify_tier_step_duration_seconds";

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

    /**
     * Phase 2.6 plan item 2 — escalation rate dashboard. Distribution
     * summary of the number of cascade steps per call. Length 1 means
     * the first tier accepted; longer = more escalations. Operators
     * read p95 / mean to see how often the cascade actually escalates
     * — a tight low-value distribution is the happy case.
     */
    public void recordCascadeSteps(int count) {
        if (count < 0) return;
        io.micrometer.core.instrument.DistributionSummary.builder(CASCADE_STEPS)
                .description("Number of cascade steps taken per classify call")
                .baseUnit("steps")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry)
                .record(count);
    }

    /**
     * Phase 2.6 plan item 4 — per-tier-step latency. Records the
     * duration of an individual cascade step, tagged by {@code tier}
     * and {@code accepted} (whether this step's result was accepted
     * or fell through to the next tier). Operators get p50/p95/p99
     * latency per tier and per outcome — fall-through latency is the
     * cost of a wasted attempt; accepted latency is the useful work.
     */
    public void recordTierStepDuration(String tier, boolean accepted, long durationMs) {
        if (durationMs < 0) return;
        Tags tags = Tags.of(
                Tag.of("tier", safe(tier)),
                Tag.of("accepted", Boolean.toString(accepted)));
        registry.timer(TIER_STEP_DURATION, tags)
                .record(durationMs, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    private static String safe(String s) {
        return s == null || s.isBlank() ? "unknown" : s.toLowerCase();
    }
}
