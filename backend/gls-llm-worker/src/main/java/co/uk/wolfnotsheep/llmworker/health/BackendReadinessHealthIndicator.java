package co.uk.wolfnotsheep.llmworker.health;

import co.uk.wolfnotsheep.llmworker.backend.LlmService;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Actuator readiness gate. Flips UP only when the {@link LlmService}
 * reports a configured + reachable backend. Kubernetes / Compose
 * health checks should treat replicas as not ready while the backend
 * is unconfigured.
 */
@Component("llmBackendReadiness")
public class BackendReadinessHealthIndicator implements HealthIndicator {

    private final LlmService slmService;

    public BackendReadinessHealthIndicator(LlmService slmService) {
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
