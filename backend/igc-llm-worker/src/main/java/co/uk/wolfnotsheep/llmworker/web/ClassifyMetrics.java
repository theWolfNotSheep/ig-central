package co.uk.wolfnotsheep.llmworker.web;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

@Component
public class ClassifyMetrics {

    private static final String DURATION = "igc_llm_classify_duration_seconds";
    private static final String RESULT = "igc_llm_classify_result_total";

    private final MeterRegistry registry;

    public ClassifyMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public Timer.Sample startTimer() {
        return Timer.start(registry);
    }

    public void recordSuccess(Timer.Sample sample, String backend) {
        Tags tags = Tags.of(
                Tag.of("outcome", "success"),
                Tag.of("backend", safe(backend)));
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

    private static String safe(String s) {
        return s == null || s.isBlank() ? "unknown" : s.toLowerCase();
    }
}
