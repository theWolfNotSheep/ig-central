package co.uk.wolfnotsheep.llmworker.backend;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class McpAvailabilityProbeTest {

    @Test
    void initial_state_is_optimistic_available() {
        McpAvailabilityProbe probe = new McpAvailabilityProbe(
                "http://does-not-resolve:9999", "/actuator/health", Duration.ofMillis(100));
        assertThat(probe.isAvailable()).isTrue();
    }

    @Test
    void empty_url_means_mcp_not_configured() {
        McpAvailabilityProbe probe = new McpAvailabilityProbe(
                "", "/actuator/health", Duration.ofMillis(100));
        assertThat(probe.isConfigured()).isFalse();
    }

    @Test
    void blank_url_means_mcp_not_configured() {
        McpAvailabilityProbe probe = new McpAvailabilityProbe(
                "   ", "/actuator/health", Duration.ofMillis(100));
        assertThat(probe.isConfigured()).isFalse();
    }

    @Test
    void doProbe_returns_false_for_unreachable_host() {
        McpAvailabilityProbe probe = new McpAvailabilityProbe(
                "http://does-not-resolve.test.invalid:9999",
                "/actuator/health",
                Duration.ofMillis(100));
        // Real HTTP probe that should fail fast.
        assertThat(probe.doProbe()).isFalse();
    }

    @Test
    void probe_when_unconfigured_is_a_silent_no_op() {
        McpAvailabilityProbe probe = new McpAvailabilityProbe(
                "", "/actuator/health", Duration.ofMillis(100));
        // Initial state is true; probe is a no-op when not configured.
        probe.probe();
        assertThat(probe.isAvailable()).isTrue();
    }

    @Test
    void setAvailable_visible_for_testing() {
        McpAvailabilityProbe probe = new McpAvailabilityProbe(
                "http://x:9999", "/actuator/health", Duration.ofMillis(100));
        probe.setAvailable(false);
        assertThat(probe.isAvailable()).isFalse();
        probe.setAvailable(true);
        assertThat(probe.isAvailable()).isTrue();
    }

    @Test
    void blank_probe_path_falls_back_to_default() {
        // Just verify the constructor accepts blank without NPE-ing.
        McpAvailabilityProbe probe = new McpAvailabilityProbe(
                "http://x:9999", "", Duration.ofMillis(100));
        assertThat(probe.isConfigured()).isTrue();
    }
}
