package co.uk.wolfnotsheep.extraction.archive.health;

import co.uk.wolfnotsheep.extraction.archive.parse.ArchiveType;
import co.uk.wolfnotsheep.extraction.archive.parse.ArchiveWalker;
import co.uk.wolfnotsheep.extraction.archive.parse.ArchiveWalkerDispatcher;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Reports DOWN if no archive walkers are registered (an indication
 * that the parser layer failed to wire up), or UP with a list of
 * supported types when at least one walker is present. Surfaces under
 * {@code /actuator/health/archiveDispatcher}.
 */
@Component
public class ArchiveDispatcherHealthIndicator implements HealthIndicator {

    private final ArchiveWalkerDispatcher dispatcher;

    public ArchiveDispatcherHealthIndicator(ArchiveWalkerDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Override
    public Health health() {
        Map<ArchiveType, ArchiveWalker> walkers = dispatcher.walkers();
        if (walkers.isEmpty()) {
            return Health.down().withDetail("reason", "no walkers registered").build();
        }
        return Health.up()
                .withDetail("supportedTypes", walkers.keySet().stream().map(Enum::name).toList())
                .build();
    }
}
