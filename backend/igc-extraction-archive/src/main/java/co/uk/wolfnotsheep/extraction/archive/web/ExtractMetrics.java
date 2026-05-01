package co.uk.wolfnotsheep.extraction.archive.web;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

/**
 * Cohesive home for the archive service's Micrometer instruments —
 * keeps the controller tidy and gives metric tag names a single place
 * to live.
 *
 * <p>Tags:
 *
 * <ul>
 *     <li>{@code outcome} — {@code success} / {@code failure} /
 *         {@code in_flight} / {@code cached}.</li>
 *     <li>{@code source} — bucket name from the request. Bounded by
 *         the number of source buckets in use.</li>
 *     <li>{@code archive_type} — {@code zip} / {@code mbox} /
 *         {@code pst} / {@code unknown}.</li>
 *     <li>{@code error_code} — closed taxonomy from the exception
 *         handler ({@code ARCHIVE_CORRUPT}, {@code ARCHIVE_TOO_LARGE},
 *         etc.) — bounded.</li>
 * </ul>
 */
@Component
public class ExtractMetrics {

    private static final String EXTRACT_DURATION = "igc_archive_duration_seconds";
    private static final String EXTRACT_RESULT = "igc_archive_result_total";
    private static final String CHILD_COUNT = "igc_archive_children";
    private static final String BYTES_PROCESSED = "igc_archive_bytes_processed";

    private final MeterRegistry registry;

    public ExtractMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public Timer.Sample startTimer() {
        return Timer.start(registry);
    }

    public void recordSuccess(Timer.Sample sample, String sourceBucket, String archiveType, int childCount, long byteCount) {
        Tags tags = Tags.of(
                Tag.of("outcome", "success"),
                Tag.of("source", safeBucket(sourceBucket)),
                Tag.of("archive_type", safeType(archiveType)));
        sample.stop(registry.timer(EXTRACT_DURATION, tags));
        registry.counter(EXTRACT_RESULT, tags).increment();
        registry.summary(CHILD_COUNT, Tags.of(Tag.of("archive_type", safeType(archiveType))))
                .record(childCount);
        registry.summary(BYTES_PROCESSED).record(byteCount);
    }

    public void recordFailure(Timer.Sample sample, String sourceBucket, String errorCode) {
        Tags tags = Tags.of(
                Tag.of("outcome", "failure"),
                Tag.of("source", safeBucket(sourceBucket)),
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

    private static String safeBucket(String bucket) {
        return bucket == null || bucket.isBlank() ? "unknown" : bucket;
    }

    private static String safeType(String type) {
        return type == null || type.isBlank() ? "unknown" : type.toLowerCase();
    }
}
