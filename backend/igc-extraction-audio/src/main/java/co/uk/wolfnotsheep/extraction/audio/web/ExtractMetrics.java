package co.uk.wolfnotsheep.extraction.audio.web;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

@Component
public class ExtractMetrics {

    private static final String EXTRACT_DURATION = "igc_audio_duration_seconds";
    private static final String EXTRACT_RESULT = "igc_audio_result_total";
    private static final String BYTES_PROCESSED = "igc_audio_bytes_processed";

    private final MeterRegistry registry;

    public ExtractMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public Timer.Sample startTimer() {
        return Timer.start(registry);
    }

    public void recordSuccess(Timer.Sample sample, String sourceBucket, String provider, long byteCount) {
        Tags tags = Tags.of(
                Tag.of("outcome", "success"),
                Tag.of("source", safe(sourceBucket)),
                Tag.of("provider", safe(provider)));
        sample.stop(registry.timer(EXTRACT_DURATION, tags));
        registry.counter(EXTRACT_RESULT, tags).increment();
        registry.summary(BYTES_PROCESSED).record(byteCount);
    }

    public void recordFailure(Timer.Sample sample, String sourceBucket, String errorCode) {
        Tags tags = Tags.of(
                Tag.of("outcome", "failure"),
                Tag.of("source", safe(sourceBucket)),
                Tag.of("error_code", errorCode));
        sample.stop(registry.timer(EXTRACT_DURATION, tags));
        registry.counter(EXTRACT_RESULT, tags).increment();
    }

    public void recordIdempotencyShortCircuit(String outcome, String sourceBucket) {
        Tags tags = Tags.of(
                Tag.of("outcome", outcome),
                Tag.of("source", safe(sourceBucket)));
        registry.counter(EXTRACT_RESULT, tags).increment();
    }

    private static String safe(String s) {
        return s == null || s.isBlank() ? "unknown" : s;
    }
}
