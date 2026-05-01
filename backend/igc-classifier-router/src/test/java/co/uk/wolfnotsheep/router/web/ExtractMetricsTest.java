package co.uk.wolfnotsheep.router.web;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExtractMetricsTest {

    private SimpleMeterRegistry registry;
    private ExtractMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new ExtractMetrics(registry);
    }

    @Test
    void recordTierByCategory_increments_counter_with_lowercased_tier_and_category() {
        metrics.recordTierByCategory("BERT", "HR_LETTERS");

        assertThat(registry.get("igc_router_classify_tier_by_category_total")
                .tags("tier", "bert", "category", "hr_letters").counter().count())
                .isEqualTo(1.0);
    }

    @Test
    void recordTierByCategory_with_null_or_blank_uses_unknown() {
        metrics.recordTierByCategory(null, null);
        metrics.recordTierByCategory("", " ");
        metrics.recordTierByCategory("LLM", "");

        assertThat(registry.get("igc_router_classify_tier_by_category_total")
                .tags("tier", "unknown", "category", "unknown").counter().count())
                .isEqualTo(2.0);
        assertThat(registry.get("igc_router_classify_tier_by_category_total")
                .tags("tier", "llm", "category", "unknown").counter().count())
                .isEqualTo(1.0);
    }

    @Test
    void recordCost_increments_by_costUnits_and_skips_non_positive() {
        metrics.recordCost("LLM", 47L);
        metrics.recordCost("LLM", 0L);
        metrics.recordCost("LLM", -3L);
        metrics.recordCost("BERT", 1L);

        assertThat(registry.get("igc_router_classify_cost_units_total")
                .tag("tier", "llm").counter().count())
                .isEqualTo(47.0);
        assertThat(registry.get("igc_router_classify_cost_units_total")
                .tag("tier", "bert").counter().count())
                .isEqualTo(1.0);
    }

    @Test
    void recordCost_with_null_tier_falls_to_unknown() {
        metrics.recordCost(null, 100L);

        assertThat(registry.get("igc_router_classify_cost_units_total")
                .tag("tier", "unknown").counter().count())
                .isEqualTo(100.0);
    }

    @Test
    void recordCascadeSteps_records_distribution() {
        metrics.recordCascadeSteps(1);
        metrics.recordCascadeSteps(2);
        metrics.recordCascadeSteps(3);

        var summary = registry.find("igc_router_classify_cascade_steps").summary();
        assertThat(summary).isNotNull();
        assertThat(summary.count()).isEqualTo(3);
        assertThat(summary.mean()).isEqualTo(2.0);
    }

    @Test
    void recordCascadeSteps_skips_negative() {
        metrics.recordCascadeSteps(-1);

        assertThat(registry.find("igc_router_classify_cascade_steps").summary()).isNull();
    }

    @Test
    void recordTierStepDuration_tags_tier_and_accepted() {
        metrics.recordTierStepDuration("BERT", false, 12);
        metrics.recordTierStepDuration("BERT", false, 8);
        metrics.recordTierStepDuration("LLM", true, 250);

        var bertFallthrough = registry.get("igc_router_classify_tier_step_duration_seconds")
                .tags("tier", "bert", "accepted", "false").timer();
        assertThat(bertFallthrough.count()).isEqualTo(2);
        assertThat(bertFallthrough.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS))
                .isEqualTo(20.0);

        var llmAccepted = registry.get("igc_router_classify_tier_step_duration_seconds")
                .tags("tier", "llm", "accepted", "true").timer();
        assertThat(llmAccepted.count()).isEqualTo(1);
        assertThat(llmAccepted.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS))
                .isEqualTo(250.0);
    }

    @Test
    void recordTierStepDuration_skips_negative_duration() {
        metrics.recordTierStepDuration("BERT", true, -1);

        assertThat(registry.find("igc_router_classify_tier_step_duration_seconds").timer())
                .isNull();
    }
}
