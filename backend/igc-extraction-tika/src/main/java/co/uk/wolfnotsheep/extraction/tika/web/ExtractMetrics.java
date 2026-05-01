package co.uk.wolfnotsheep.extraction.tika.web;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Cohesive home for the extraction service's Micrometer instruments —
 * keeps the controller tidy and gives metric tag names a single place
 * to live.
 *
 * <p>Tags:
 *
 * <ul>
 *     <li>{@code outcome} — {@code success} / {@code failure} /
 *         {@code in_flight} / {@code cached}. Cardinality 4.</li>
 *     <li>{@code source} — bucket name from the request. Cardinality
 *         is bounded by the number of source buckets (currently small;
 *         revisit when external connector buckets land).</li>
 *     <li>{@code mime_family} — the major slash-prefix of Tika's
 *         detected mime, or {@code unknown}. Bounded set
 *         ({@code application}, {@code text}, {@code image},
 *         {@code audio}, {@code video}, {@code message}, {@code unknown}).</li>
 * </ul>
 *
 * <p>Cardinality matters: Prometheus stores one time-series per unique
 * combination of tag values. Avoid free-form tag values (full mime
 * strings, error messages, document ids).
 */
@Component
public class ExtractMetrics {

    private static final String EXTRACT_DURATION = "igc_extraction_duration_seconds";
    private static final String EXTRACT_RESULT = "igc_extraction_result_total";

    private final MeterRegistry registry;

    public ExtractMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public Timer.Sample startTimer() {
        return Timer.start(registry);
    }

    public void recordSuccess(Timer.Sample sample, String sourceBucket, String detectedMime) {
        Tags tags = Tags.of(
                Tag.of("outcome", "success"),
                Tag.of("source", safeBucket(sourceBucket)),
                Tag.of("mime_family", mimeFamily(detectedMime)));
        sample.stop(registry.timer(EXTRACT_DURATION, tags));
        registry.counter(EXTRACT_RESULT, tags).increment();
    }

    public void recordFailure(Timer.Sample sample, String sourceBucket, String errorCode) {
        Tags tags = Tags.of(
                Tag.of("outcome", "failure"),
                Tag.of("source", safeBucket(sourceBucket)),
                // errorCode comes from a closed taxonomy in
                // ExtractController — bounded, safe to tag.
                Tag.of("error_code", errorCode));
        sample.stop(registry.timer(EXTRACT_DURATION, tags));
        registry.counter(EXTRACT_RESULT, tags).increment();
    }

    public void recordIdempotencyShortCircuit(String outcome, String sourceBucket) {
        Tags tags = Tags.of(
                Tag.of("outcome", outcome),
                Tag.of("source", safeBucket(sourceBucket)));
        registry.counter(EXTRACT_RESULT, tags).increment();
    }

    /** Reports an explicit per-extract byte-count gauge for capacity-planning. */
    public void recordByteCount(long byteCount) {
        registry.summary("igc_extraction_bytes_processed").record(byteCount);
    }

    /** Reports the wall-clock duration for a non-Sample-driven event (cached replay). */
    public void recordDuration(String outcome, Duration duration) {
        registry.timer("igc_extraction_duration_seconds", Tags.of(Tag.of("outcome", outcome)))
                .record(duration);
    }

    private static String safeBucket(String bucket) {
        return bucket == null || bucket.isBlank() ? "unknown" : bucket;
    }

    private static String mimeFamily(String mime) {
        if (mime == null || mime.isBlank()) {
            return "unknown";
        }
        int slash = mime.indexOf('/');
        return slash <= 0 ? "unknown" : mime.substring(0, slash);
    }
}
