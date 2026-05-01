package co.uk.wolfnotsheep.bert.health;

import co.uk.wolfnotsheep.bert.inference.InferenceEngine;
import co.uk.wolfnotsheep.bert.registry.ModelRegistry;
import co.uk.wolfnotsheep.bert.registry.ReloadCoordinator;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Reports {@code OUT_OF_SERVICE} (which Spring Boot maps to 503 on
 * the actuator surface) when no model is loaded or a reload is in
 * flight. Class C scaling profile per architecture §Scaling profile
 * — replicas aren't interchangeable until they've warmed up.
 */
@Component
public class ModelReadinessHealthIndicator implements HealthIndicator {

    private final InferenceEngine engine;
    private final ModelRegistry registry;
    private final ReloadCoordinator reloadCoordinator;

    public ModelReadinessHealthIndicator(
            InferenceEngine engine,
            ModelRegistry registry,
            ReloadCoordinator reloadCoordinator) {
        this.engine = engine;
        this.registry = registry;
        this.reloadCoordinator = reloadCoordinator;
    }

    @Override
    public Health health() {
        if (reloadCoordinator.isInProgress()) {
            return Health.outOfService()
                    .withDetail("reason", "model reload in progress")
                    .build();
        }
        if (!engine.isReady() || registry.isEmpty()) {
            return Health.outOfService()
                    .withDetail("reason", "no model loaded")
                    .withDetail("hint", "set igc.bert.inference.engine=djl + minio config once trainer publishes ONNX")
                    .build();
        }
        return Health.up()
                .withDetail("loadedModels", registry.snapshot().size())
                .build();
    }
}
