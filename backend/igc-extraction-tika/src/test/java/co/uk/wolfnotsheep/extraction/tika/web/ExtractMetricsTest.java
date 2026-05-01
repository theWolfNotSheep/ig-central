package co.uk.wolfnotsheep.extraction.tika.web;

import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExtractMetricsTest {

    @Test
    void success_records_duration_and_counter_with_mime_family() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ExtractMetrics metrics = new ExtractMetrics(registry);

        Timer.Sample sample = metrics.startTimer();
        metrics.recordSuccess(sample, "uploads", "application/pdf");

        double counterCount = registry.counter("igc_extraction_result_total", Tags.of(
                Tag.of("outcome", "success"),
                Tag.of("source", "uploads"),
                Tag.of("mime_family", "application"))).count();
        long timerCount = registry.timer("igc_extraction_duration_seconds", Tags.of(
                Tag.of("outcome", "success"),
                Tag.of("source", "uploads"),
                Tag.of("mime_family", "application"))).count();
        assertThat(counterCount).isEqualTo(1L);
        assertThat(timerCount).isEqualTo(1L);
    }

    @Test
    void failure_records_with_error_code_tag() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ExtractMetrics metrics = new ExtractMetrics(registry);

        Timer.Sample sample = metrics.startTimer();
        metrics.recordFailure(sample, "uploads", "EXTRACTION_CORRUPT");

        assertThat(registry.counter("igc_extraction_result_total", Tags.of(
                Tag.of("outcome", "failure"),
                Tag.of("source", "uploads"),
                Tag.of("error_code", "EXTRACTION_CORRUPT"))).count()).isEqualTo(1L);
    }

    @Test
    void mime_family_is_capped_at_known_prefixes_with_unknown_fallback() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ExtractMetrics metrics = new ExtractMetrics(registry);

        Timer.Sample sample = metrics.startTimer();
        metrics.recordSuccess(sample, "uploads", null);
        metrics.recordSuccess(metrics.startTimer(), "uploads", "garbage-no-slash");

        assertThat(registry.counter("igc_extraction_result_total", Tags.of(
                Tag.of("outcome", "success"),
                Tag.of("source", "uploads"),
                Tag.of("mime_family", "unknown"))).count()).isEqualTo(2L);
    }

    @Test
    void blank_bucket_falls_back_to_unknown() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ExtractMetrics metrics = new ExtractMetrics(registry);

        metrics.recordIdempotencyShortCircuit("cached", null);
        metrics.recordIdempotencyShortCircuit("cached", "");

        assertThat(registry.counter("igc_extraction_result_total", Tags.of(
                Tag.of("outcome", "cached"),
                Tag.of("source", "unknown"))).count()).isEqualTo(2L);
    }
}
