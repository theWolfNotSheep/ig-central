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

    private static String safe(String s) {
        return s == null || s.isBlank() ? "unknown" : s.toLowerCase();
    }
}
