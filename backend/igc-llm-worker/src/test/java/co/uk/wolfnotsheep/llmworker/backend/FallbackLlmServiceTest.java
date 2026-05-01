package co.uk.wolfnotsheep.llmworker.backend;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
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

class FallbackLlmServiceTest {

    private LlmService primary;
    private LlmService secondary;

    @BeforeEach
    void setUp() {
        primary = mock(LlmService.class);
        secondary = mock(LlmService.class);
        when(primary.activeBackend()).thenReturn(LlmBackendId.ANTHROPIC);
        when(secondary.activeBackend()).thenReturn(LlmBackendId.OLLAMA);
    }

    private static LlmResult result(LlmBackendId backend) {
        return new LlmResult(
                Map.of("category", "HR"), 0.9f, "from " + backend.name().toLowerCase(),
                backend, "model", 10L, 5L);
    }

    @Test
    void primary_success_returns_primary_result_no_secondary_call() {
        when(primary.classify(any(), any(), any())).thenReturn(result(LlmBackendId.ANTHROPIC));
        FallbackLlmService fallback = new FallbackLlmService(primary, secondary);

        LlmResult got = fallback.classify("blk", 1, "doc");

        assertThat(got.backend()).isEqualTo(LlmBackendId.ANTHROPIC);
        verify(secondary, never()).classify(any(), any(), any());
    }

    @Test
    void primary_runtime_exception_falls_through_to_secondary() {
        when(primary.classify(any(), any(), any()))
                .thenThrow(new UncheckedIOException("upstream fail", new IOException()));
        when(secondary.classify(any(), any(), any())).thenReturn(result(LlmBackendId.OLLAMA));
        FallbackLlmService fallback = new FallbackLlmService(primary, secondary);

        LlmResult got = fallback.classify("blk", 1, "doc");

        assertThat(got.backend()).isEqualTo(LlmBackendId.OLLAMA);
        verify(secondary, times(1)).classify("blk", 1, "doc");
    }

    @Test
    void primary_circuit_breaker_open_falls_through_to_secondary() {
        when(primary.classify(any(), any(), any()))
                .thenThrow(new CircuitBreakerOpenException("ANTHROPIC"));
        when(secondary.classify(any(), any(), any())).thenReturn(result(LlmBackendId.OLLAMA));
        FallbackLlmService fallback = new FallbackLlmService(primary, secondary);

        LlmResult got = fallback.classify("blk", 1, "doc");

        assertThat(got.backend()).isEqualTo(LlmBackendId.OLLAMA);
    }

    @Test
    void budget_exceeded_does_not_fall_through() {
        when(primary.classify(any(), any(), any()))
                .thenThrow(new BudgetExceededException("daily cap", 60L));
        FallbackLlmService fallback = new FallbackLlmService(primary, secondary);

        assertThatThrownBy(() -> fallback.classify("blk", 1, "doc"))
                .isInstanceOf(BudgetExceededException.class);
        verify(secondary, never()).classify(any(), any(), any());
    }

    @Test
    void rate_limit_exceeded_does_not_fall_through() {
        when(primary.classify(any(), any(), any()))
                .thenThrow(new RateLimitExceededException("no permits"));
        FallbackLlmService fallback = new FallbackLlmService(primary, secondary);

        assertThatThrownBy(() -> fallback.classify("blk", 1, "doc"))
                .isInstanceOf(RateLimitExceededException.class);
        verify(secondary, never()).classify(any(), any(), any());
    }

    @Test
    void secondary_failure_propagates() {
        when(primary.classify(any(), any(), any()))
                .thenThrow(new UncheckedIOException("primary down", new IOException()));
        when(secondary.classify(any(), any(), any()))
                .thenThrow(new UncheckedIOException("secondary down too", new IOException()));
        FallbackLlmService fallback = new FallbackLlmService(primary, secondary);

        assertThatThrownBy(() -> fallback.classify("blk", 1, "doc"))
                .isInstanceOf(UncheckedIOException.class)
                .hasMessageContaining("secondary down too");
    }

    @Test
    void activeBackend_returns_primary_even_when_secondary_handled_call() {
        FallbackLlmService fallback = new FallbackLlmService(primary, secondary);
        assertThat(fallback.activeBackend()).isEqualTo(LlmBackendId.ANTHROPIC);
    }

    @Test
    void isReady_true_when_either_backend_is_ready() {
        when(primary.isReady()).thenReturn(false);
        when(secondary.isReady()).thenReturn(true);
        FallbackLlmService fallback = new FallbackLlmService(primary, secondary);

        assertThat(fallback.isReady()).isTrue();
    }

    @Test
    void isReady_false_only_when_both_backends_are_not_ready() {
        when(primary.isReady()).thenReturn(false);
        when(secondary.isReady()).thenReturn(false);
        FallbackLlmService fallback = new FallbackLlmService(primary, secondary);

        assertThat(fallback.isReady()).isFalse();
    }

    @Test
    void fallback_invocation_counter_tags_with_primary_and_reason() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        when(primary.classify(any(), any(), any()))
                .thenThrow(new CircuitBreakerOpenException("ANTHROPIC"))
                .thenThrow(new UncheckedIOException("boom", new IOException()));
        when(secondary.classify(any(), any(), any())).thenReturn(result(LlmBackendId.OLLAMA));
        FallbackLlmService fallback = new FallbackLlmService(primary, secondary, registry);

        fallback.classify("blk", 1, "doc");
        fallback.classify("blk", 1, "doc");

        assertThat(registry.get("llm.fallback.invocations")
                .tags("primary", "anthropic", "reason", "circuit_breaker_open").counter().count())
                .isEqualTo(1.0);
        assertThat(registry.get("llm.fallback.invocations")
                .tags("primary", "anthropic", "reason", "primary_failed").counter().count())
                .isEqualTo(1.0);
    }

    @Test
    void no_meter_registry_does_not_break_fallback() {
        when(primary.classify(any(), any(), any()))
                .thenThrow(new UncheckedIOException("primary down", new IOException()));
        when(secondary.classify(eq("blk"), eq(1), any())).thenReturn(result(LlmBackendId.OLLAMA));
        FallbackLlmService fallback = new FallbackLlmService(primary, secondary);

        LlmResult got = fallback.classify("blk", 1, "doc");
        assertThat(got).isNotNull();
    }
}
