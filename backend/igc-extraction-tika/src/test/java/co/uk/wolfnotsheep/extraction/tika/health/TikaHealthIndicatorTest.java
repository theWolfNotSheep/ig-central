package co.uk.wolfnotsheep.extraction.tika.health;

import co.uk.wolfnotsheep.extraction.tika.parse.TikaExtractionService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

import static org.assertj.core.api.Assertions.assertThat;

class TikaHealthIndicatorTest {

    @Test
    void healthy_tika_reports_UP() {
        TikaHealthIndicator probe = new TikaHealthIndicator(new TikaExtractionService());

        Health h = probe.health();

        assertThat(h.getStatus()).isEqualTo(Status.UP);
    }
}
