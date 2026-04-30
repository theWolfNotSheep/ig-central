package co.uk.wolfnotsheep.llmworker.backend;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CircuitBreakerLlmServiceTest {

    private LlmService delegate;
    private CircuitBreaker breaker;
    private CircuitBreakerLlmService cb;

    @BeforeEach
    void setUp() {
        delegate = mock(LlmService.class);
        when(delegate.activeBackend()).thenReturn(LlmBackendId.ANTHROPIC);
        breaker = new CircuitBreaker("test", 3, Duration.ofSeconds(30));
        cb = new CircuitBreakerLlmService(delegate, breaker);
    }

    private LlmResult sampleResult() {
        return new LlmResult(
                Map.of("category", "HR"), 0.9f, "rationale",
                LlmBackendId.ANTHROPIC, "claude-sonnet-4-5", 100L, 50L);
    }

    @Test
    void successful_call_passes_through_and_resets_failures() {
        when(delegate.classify(eq("blk"), eq(1), any())).thenReturn(sampleResult());

        LlmResult result = cb.classify("blk", 1, "doc");

        assertThat(result).isNotNull();
        verify(delegate, times(1)).classify("blk", 1, "doc");
        assertThat(breaker.currentState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void runtime_exception_from_delegate_records_failure() {
        when(delegate.classify(any(), any(), any()))
                .thenThrow(new UncheckedIOException("boom", new IOException()));

        assertThatThrownBy(() -> cb.classify("blk", 1, "doc"))
                .isInstanceOf(UncheckedIOException.class);

        assertThat(breaker.consecutiveFailures()).isEqualTo(1);
    }

    @Test
    void threshold_failures_open_breaker_and_short_circuit_subsequent_calls() {
        when(delegate.classify(any(), any(), any()))
                .thenThrow(new UncheckedIOException("boom", new IOException()));

        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> cb.classify("blk", 1, "doc"))
                    .isInstanceOf(UncheckedIOException.class);
        }
        assertThat(breaker.currentState()).isEqualTo(CircuitBreaker.State.OPEN);

        assertThatThrownBy(() -> cb.classify("blk", 1, "doc"))
                .isInstanceOf(CircuitBreakerOpenException.class);

        // Delegate was called 3 times (the threshold) + 0 times after open.
        verify(delegate, times(3)).classify(any(), any(), any());
    }

    @Test
    void BudgetExceeded_does_not_count_as_failure() {
        when(delegate.classify(any(), any(), any()))
                .thenThrow(new BudgetExceededException("daily cap", 60L));

        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> cb.classify("blk", 1, "doc"))
                    .isInstanceOf(BudgetExceededException.class);
        }
        assertThat(breaker.currentState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(breaker.consecutiveFailures()).isZero();
    }

    @Test
    void RateLimitExceeded_does_not_count_as_failure() {
        when(delegate.classify(any(), any(), any()))
                .thenThrow(new RateLimitExceededException("no permits"));

        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> cb.classify("blk", 1, "doc"))
                    .isInstanceOf(RateLimitExceededException.class);
        }
        assertThat(breaker.currentState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(breaker.consecutiveFailures()).isZero();
    }

    @Test
    void isReady_false_when_breaker_open() {
        when(delegate.isReady()).thenReturn(true);

        assertThat(cb.isReady()).isTrue();

        // Trip breaker.
        when(delegate.classify(any(), any(), any()))
                .thenThrow(new UncheckedIOException("boom", new IOException()));
        for (int i = 0; i < 3; i++) {
            try { cb.classify("blk", 1, "doc"); } catch (RuntimeException ignored) {}
        }
        assertThat(cb.isReady()).isFalse();
    }

    @Test
    void activeBackend_delegates_through_decorator() {
        assertThat(cb.activeBackend()).isEqualTo(LlmBackendId.ANTHROPIC);
    }

    @Test
    void successful_call_after_failures_resets_consecutive_count() {
        when(delegate.classify(any(), any(), any()))
                .thenThrow(new UncheckedIOException("boom", new IOException()))
                .thenThrow(new UncheckedIOException("boom", new IOException()))
                .thenReturn(sampleResult());

        try { cb.classify("blk", 1, "doc"); } catch (RuntimeException ignored) {}
        try { cb.classify("blk", 1, "doc"); } catch (RuntimeException ignored) {}
        assertThat(breaker.consecutiveFailures()).isEqualTo(2);

        cb.classify("blk", 1, "doc");
        assertThat(breaker.consecutiveFailures()).isZero();
        assertThat(breaker.currentState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }
}
