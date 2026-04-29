package co.uk.wolfnotsheep.slm.health;

import co.uk.wolfnotsheep.slm.backend.SlmService;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Actuator readiness gate. Flips UP only when the {@link SlmService}
 * reports a configured + reachable backend. Kubernetes / Compose
 * health checks should treat replicas as not ready while the backend
 * is unconfigured.
 */
@Component("slmBackendReadiness")
public class BackendReadinessHealthIndicator implements HealthIndicator {

    private final SlmService slmService;

    public BackendReadinessHealthIndicator(SlmService slmService) {
        this.slmService = slmService;
    }

    @Override
    public Health health() {
        Health.Builder builder = slmService.isReady()
                ? Health.up()
                : Health.outOfService();
        return builder
                .withDetail("backend", slmService.activeBackend().name())
                .build();
    }
}
