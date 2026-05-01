package co.uk.wolfnotsheep.bert.web;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

@Component
public class InferMetrics {

    private static final String DURATION = "igc_bert_infer_duration_seconds";
    private static final String RESULT = "igc_bert_infer_result_total";
    private static final String BYTES = "igc_bert_infer_bytes_processed";

    private final MeterRegistry registry;

    public InferMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public Timer.Sample startTimer() {
        return Timer.start(registry);
    }

    public void recordSuccess(Timer.Sample sample, String modelVersion, long byteCount) {
        Tags tags = Tags.of(
                Tag.of("outcome", "success"),
                Tag.of("model_version", safe(modelVersion)));
        sample.stop(registry.timer(DURATION, tags));
        registry.counter(RESULT, tags).increment();
        registry.summary(BYTES).record(byteCount);
    }

    public void recordFailure(Timer.Sample sample, String errorCode) {
        Tags tags = Tags.of(
                Tag.of("outcome", "failure"),
                Tag.of("error_code", errorCode));
        sample.stop(registry.timer(DURATION, tags));
        registry.counter(RESULT, tags).increment();
    }

    private static String safe(String s) {
        return s == null || s.isBlank() ? "unknown" : s;
    }
}
