package co.uk.wolfnotsheep.extraction.audio.health;

import co.uk.wolfnotsheep.extraction.audio.parse.AudioTranscriptionService;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Reports {@code OUT_OF_SERVICE} when no provider is wired (callers
 * see 503 on requests; the readiness gate stays UP for the
 * application as a whole because liveness is fine — it's just that
 * the audio surface is degraded). UP otherwise.
 */
@Component
public class AudioBackendHealthIndicator implements HealthIndicator {

    private final AudioTranscriptionService backend;

    public AudioBackendHealthIndicator(AudioTranscriptionService backend) {
        this.backend = backend;
    }

    @Override
    public Health health() {
        if (!backend.isReady()) {
            return Health.outOfService()
                    .withDetail("provider", backend.providerId())
                    .withDetail("reason", "no transcription provider configured")
                    .build();
        }
        return Health.up()
                .withDetail("provider", backend.providerId())
                .build();
    }
}
