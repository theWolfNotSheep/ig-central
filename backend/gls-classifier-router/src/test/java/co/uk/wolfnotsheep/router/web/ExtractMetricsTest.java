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

        assertThat(registry.get("gls_router_classify_tier_by_category_total")
                .tags("tier", "bert", "category", "hr_letters").counter().count())
                .isEqualTo(1.0);
    }

    @Test
    void recordTierByCategory_with_null_or_blank_uses_unknown() {
        metrics.recordTierByCategory(null, null);
        metrics.recordTierByCategory("", " ");
        metrics.recordTierByCategory("LLM", "");

        assertThat(registry.get("gls_router_classify_tier_by_category_total")
                .tags("tier", "unknown", "category", "unknown").counter().count())
                .isEqualTo(2.0);
        assertThat(registry.get("gls_router_classify_tier_by_category_total")
                .tags("tier", "llm", "category", "unknown").counter().count())
                .isEqualTo(1.0);
    }

    @Test
    void recordCost_increments_by_costUnits_and_skips_non_positive() {
        metrics.recordCost("LLM", 47L);
        metrics.recordCost("LLM", 0L);
        metrics.recordCost("LLM", -3L);
        metrics.recordCost("BERT", 1L);

        assertThat(registry.get("gls_router_classify_cost_units_total")
                .tag("tier", "llm").counter().count())
                .isEqualTo(47.0);
        assertThat(registry.get("gls_router_classify_cost_units_total")
                .tag("tier", "bert").counter().count())
                .isEqualTo(1.0);
    }

    @Test
    void recordCost_with_null_tier_falls_to_unknown() {
        metrics.recordCost(null, 100L);

        assertThat(registry.get("gls_router_classify_cost_units_total")
                .tag("tier", "unknown").counter().count())
                .isEqualTo(100.0);
    }
}
