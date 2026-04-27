package co.uk.wolfnotsheep.extraction.tika.health;

import co.uk.wolfnotsheep.extraction.tika.parse.TikaExtractionService;
import co.uk.wolfnotsheep.extraction.tika.parse.UnparseableDocumentException;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

/**
 * Probes the in-process {@link TikaExtractionService} on every actuator
 * health check by parsing a tiny synthetic input. If Tika hasn't loaded
 * its parser registry, this surfaces under
 * {@code /actuator/health/tika} as DOWN — preventing the readiness
 * gate from flipping UP before the service can actually do its job.
 *
 * <p>The probe input is one short ASCII string, so the cost per check
 * is microseconds. Don't call this on a request-hot path.
 */
@Component
public class TikaHealthIndicator implements HealthIndicator {

    private static final byte[] PROBE = "ok".getBytes(StandardCharsets.UTF_8);

    private final TikaExtractionService tika;

    public TikaHealthIndicator(TikaExtractionService tika) {
        this.tika = tika;
    }

    @Override
    public Health health() {
        try {
            tika.extract(new ByteArrayInputStream(PROBE), "probe.txt");
            return Health.up().build();
        } catch (UnparseableDocumentException e) {
            // Tika rejected our probe — registry is initialised but
            // something is fundamentally wrong with parser dispatch.
            return Health.down().withDetail("error", "Tika rejected probe: " + e.getMessage()).build();
        } catch (RuntimeException e) {
            return Health.down().withException(e).build();
        }
    }
}
