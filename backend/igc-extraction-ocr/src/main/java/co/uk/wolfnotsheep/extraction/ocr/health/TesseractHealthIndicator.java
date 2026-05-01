package co.uk.wolfnotsheep.extraction.ocr.health;

import net.sourceforge.tess4j.Tesseract;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.Arrays;

/**
 * Reports the readiness of the Tesseract runtime: native library
 * loadable + the configured tessdata directory exists with at least
 * one language pack present. DOWN means the deployment is missing
 * the apt packages or the language data — surfaces under
 * {@code /actuator/health/tesseract}.
 */
@Component
public class TesseractHealthIndicator implements HealthIndicator {

    private final String tessdataPath;

    public TesseractHealthIndicator(
            @Value("${igc.extraction.ocr.tessdata-path:/usr/share/tesseract-ocr/4.00/tessdata}") String tessdataPath) {
        this.tessdataPath = tessdataPath;
    }

    @Override
    public Health health() {
        File dir = new File(tessdataPath);
        if (!dir.isDirectory()) {
            return Health.down()
                    .withDetail("reason", "tessdata directory not found")
                    .withDetail("path", tessdataPath).build();
        }
        String[] entries = dir.list((d, name) -> name.endsWith(".traineddata"));
        if (entries == null || entries.length == 0) {
            return Health.down()
                    .withDetail("reason", "no .traineddata files in tessdata")
                    .withDetail("path", tessdataPath).build();
        }
        // Probe the JNA bridge — instantiating Tesseract is cheap and
        // doesn't actually run OCR, but it surfaces native-library
        // load failures.
        try {
            new Tesseract();
        } catch (Throwable t) {
            return Health.down().withException(t).build();
        }
        return Health.up()
                .withDetail("path", tessdataPath)
                .withDetail("languagePacks", Arrays.stream(entries)
                        .map(s -> s.replace(".traineddata", ""))
                        .toList())
                .build();
    }
}
