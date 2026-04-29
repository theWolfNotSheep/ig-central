package co.uk.wolfnotsheep.llmworker.backend;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateLimitGateTest {

    @Test
    void disabled_when_permits_is_zero() {
        RateLimitGate gate = new RateLimitGate(0, 0L);
        assertThat(gate.totalPermits()).isEqualTo(0);
        assertThat(gate.availablePermits()).isEqualTo(-1);

        // Acquiring is a no-op token.
        try (var token = gate.acquire()) {
            assertThat(token).isNotNull();
        }
    }

    @Test
    void permits_can_be_acquired_and_released_via_try_with_resources() {
        RateLimitGate gate = new RateLimitGate(2, 100L);
        assertThat(gate.availablePermits()).isEqualTo(2);

        try (var t1 = gate.acquire()) {
            assertThat(gate.availablePermits()).isEqualTo(1);
            try (var t2 = gate.acquire()) {
                assertThat(gate.availablePermits()).isEqualTo(0);
            }
            // t2 closed → permit released.
            assertThat(gate.availablePermits()).isEqualTo(1);
        }
        assertThat(gate.availablePermits()).isEqualTo(2);
    }

    @Test
    void throws_RateLimitExceededException_when_no_permit_available() {
        RateLimitGate gate = new RateLimitGate(1, 50L);
        // Acquire the only permit and hold it.
        var held = gate.acquire();

        assertThatThrownBy(gate::acquire)
                .isInstanceOf(RateLimitExceededException.class);

        held.close();
    }

    @Test
    void release_is_idempotent() {
        RateLimitGate gate = new RateLimitGate(1, 0L);
        var token = gate.acquire();
        token.close();
        token.close(); // second close is a no-op
        assertThat(gate.availablePermits()).isEqualTo(1);
    }

    @Test
    void multiple_threads_are_bounded_to_permit_count() throws Exception {
        RateLimitGate gate = new RateLimitGate(3, 200L);
        List<Thread> threads = new ArrayList<>();
        List<RateLimitGate.Token> held = java.util.Collections.synchronizedList(new ArrayList<>());

        // Three permits — three threads acquire.
        for (int i = 0; i < 3; i++) {
            Thread t = new Thread(() -> held.add(gate.acquire()));
            threads.add(t);
            t.start();
        }
        for (Thread t : threads) t.join();
        assertThat(held).hasSize(3);
        assertThat(gate.availablePermits()).isEqualTo(0);

        // A fourth must wait + then time out.
        assertThatThrownBy(gate::acquire)
                .isInstanceOf(RateLimitExceededException.class);

        // Release all.
        held.forEach(RateLimitGate.Token::close);
        assertThat(gate.availablePermits()).isEqualTo(3);
    }
}
