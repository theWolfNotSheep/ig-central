package co.uk.wolfnotsheep.router.parse;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RateLimitGateTest {

    @SuppressWarnings("unchecked")
    private static ObjectProvider<io.micrometer.core.instrument.MeterRegistry> providerOf(
            io.micrometer.core.instrument.MeterRegistry mr) {
        ObjectProvider<io.micrometer.core.instrument.MeterRegistry> p = mock(ObjectProvider.class);
        when(p.getIfAvailable()).thenReturn(mr);
        return p;
    }

    @Test
    void zero_permits_means_disabled_pass_through() {
        RateLimitGate gate = new RateLimitGate(0, 0L, providerOf(null));

        try (RateLimitGate.Token t = gate.acquire()) {
            assertThat(t).isNotNull();
        }
        assertThat(gate.availablePermits()).isEqualTo(-1);
        assertThat(gate.totalPermits()).isZero();
    }

    @Test
    void single_permit_allows_one_caller_at_a_time() {
        RateLimitGate gate = new RateLimitGate(1, 0L, providerOf(null));

        RateLimitGate.Token first = gate.acquire();
        assertThat(gate.availablePermits()).isZero();

        // Second acquire with wait-ms=0 should fail immediately.
        assertThatThrownBy(gate::acquire).isInstanceOf(RateLimitExceededException.class);

        first.close();
        assertThat(gate.availablePermits()).isEqualTo(1);

        // After release, acquire succeeds again.
        try (RateLimitGate.Token t = gate.acquire()) {
            assertThat(t).isNotNull();
        }
    }

    @Test
    void wait_ms_blocks_briefly_before_failing() {
        RateLimitGate gate = new RateLimitGate(1, 50L, providerOf(null));
        RateLimitGate.Token first = gate.acquire();

        long started = System.currentTimeMillis();
        assertThatThrownBy(gate::acquire).isInstanceOf(RateLimitExceededException.class);
        long elapsed = System.currentTimeMillis() - started;
        assertThat(elapsed).isGreaterThanOrEqualTo(40L);
        first.close();
    }

    @Test
    void try_with_resources_releases_permit() {
        RateLimitGate gate = new RateLimitGate(2, 0L, providerOf(null));
        try (RateLimitGate.Token t = gate.acquire()) {
            assertThat(gate.availablePermits()).isEqualTo(1);
        }
        assertThat(gate.availablePermits()).isEqualTo(2);
    }

    @Test
    void close_is_idempotent() {
        RateLimitGate gate = new RateLimitGate(1, 0L, providerOf(null));
        RateLimitGate.Token t = gate.acquire();
        t.close();
        t.close();  // second close is a no-op
        assertThat(gate.availablePermits()).isEqualTo(1);
    }

    @Test
    void registers_gauges_when_meter_registry_present() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        RateLimitGate gate = new RateLimitGate(3, 0L, providerOf(registry));

        assertThat(registry.get("router.rate_limit.permits.available").gauge().value()).isEqualTo(3.0);
        assertThat(registry.get("router.rate_limit.permits.total").gauge().value()).isEqualTo(3.0);

        try (RateLimitGate.Token t = gate.acquire()) {
            assertThat(registry.get("router.rate_limit.permits.available").gauge().value()).isEqualTo(2.0);
        }
        assertThat(registry.get("router.rate_limit.permits.available").gauge().value()).isEqualTo(3.0);
    }

    @Test
    void disabled_gate_does_not_register_gauges() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        new RateLimitGate(0, 0L, providerOf(registry));
        assertThat(registry.find("router.rate_limit.permits.available").gauge()).isNull();
        assertThat(registry.find("router.rate_limit.permits.total").gauge()).isNull();
    }
}
