package co.uk.wolfnotsheep.platformaudit.relay;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuration knobs for {@link OutboxRelay}. Bound from
 * {@code igc.platform.audit.relay.*} keys.
 *
 * @param enabled        Whether the relay scheduled task runs at all. When
 *                       false, the relay bean is still constructed but its
 *                       poll cycle is a no-op. Default {@code true}.
 * @param pollInterval   How often the relay polls the outbox for PENDING
 *                       rows. Default 5 seconds.
 * @param batchSize      Maximum rows to fetch per poll. Default 50.
 * @param maxAttempts    After this many failed publish attempts, a row is
 *                       marked {@code FAILED} and stops being retried.
 *                       Default 5.
 * @param backoffBase    Initial backoff between retries; doubled each
 *                       attempt up to {@link #backoffMax}. Default 1 second.
 * @param backoffMax     Cap on the exponential backoff. Default 5 minutes.
 * @param exchange       AMQP topic exchange the relay publishes to. The
 *                       routing key is built per envelope as
 *                       {@code audit.tier1.<eventType>} (DOMAIN) or
 *                       {@code audit.tier2.<eventType>} (SYSTEM). Default
 *                       {@code igc.audit}.
 */
@ConfigurationProperties(prefix = "igc.platform.audit.relay")
public record OutboxRelayProperties(
        boolean enabled,
        Duration pollInterval,
        int batchSize,
        int maxAttempts,
        Duration backoffBase,
        Duration backoffMax,
        String exchange
) {

    public OutboxRelayProperties {
        if (pollInterval == null) {
            pollInterval = Duration.ofSeconds(5);
        }
        if (batchSize <= 0) {
            batchSize = 50;
        }
        if (maxAttempts <= 0) {
            maxAttempts = 5;
        }
        if (backoffBase == null) {
            backoffBase = Duration.ofSeconds(1);
        }
        if (backoffMax == null) {
            backoffMax = Duration.ofMinutes(5);
        }
        if (exchange == null || exchange.isBlank()) {
            exchange = "igc.audit";
        }
    }

    public static OutboxRelayProperties defaults() {
        return new OutboxRelayProperties(true, null, 0, 0, null, null, null);
    }
}
