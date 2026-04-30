package co.uk.wolfnotsheep.llmworker.backend;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class McpCappingLlmServiceTest {

    private LlmService delegate;
    private McpAvailabilityProbe probe;

    @BeforeEach
    void setUp() {
        delegate = mock(LlmService.class);
        when(delegate.activeBackend()).thenReturn(LlmBackendId.ANTHROPIC);
        // Real probe with a configured URL — we control isAvailable() via setAvailable().
        probe = new McpAvailabilityProbe(
                "http://mcp:9999", "/actuator/health", Duration.ofMillis(100));
    }

    private static LlmResult result(float confidence) {
        return new LlmResult(
                Map.of("category", "HR"), confidence, "rationale text",
                LlmBackendId.ANTHROPIC, "claude-sonnet-4-5", 100L, 50L);
    }

    @Test
    void mcp_available_passes_through_unchanged() {
        probe.setAvailable(true);
        when(delegate.classify(eq("blk"), eq(1), any())).thenReturn(result(0.95f));
        McpCappingLlmService cap = new McpCappingLlmService(delegate, probe, 0.7f);

        LlmResult got = cap.classify("blk", 1, "doc");

        assertThat(got.confidence()).isEqualTo(0.95f);
        assertThat(got.rationale()).isEqualTo("rationale text");
    }

    @Test
    void mcp_unavailable_caps_high_confidence() {
        probe.setAvailable(false);
        when(delegate.classify(eq("blk"), eq(1), any())).thenReturn(result(0.95f));
        McpCappingLlmService cap = new McpCappingLlmService(delegate, probe, 0.7f);

        LlmResult got = cap.classify("blk", 1, "doc");

        assertThat(got.confidence()).isEqualTo(0.7f);
        assertThat(got.rationale()).contains("MCP unreachable");
    }

    @Test
    void mcp_unavailable_does_not_increase_low_confidence() {
        probe.setAvailable(false);
        when(delegate.classify(eq("blk"), eq(1), any())).thenReturn(result(0.4f));
        McpCappingLlmService cap = new McpCappingLlmService(delegate, probe, 0.7f);

        LlmResult got = cap.classify("blk", 1, "doc");

        assertThat(got.confidence()).isEqualTo(0.4f);
        assertThat(got.rationale()).isEqualTo("rationale text");
    }

    @Test
    void mcp_unavailable_with_confidence_at_cap_passes_through() {
        probe.setAvailable(false);
        when(delegate.classify(any(), any(), any())).thenReturn(result(0.7f));
        McpCappingLlmService cap = new McpCappingLlmService(delegate, probe, 0.7f);

        LlmResult got = cap.classify("blk", 1, "doc");

        assertThat(got.confidence()).isEqualTo(0.7f);
        assertThat(got.rationale()).isEqualTo("rationale text");
    }

    @Test
    void mcp_not_configured_never_caps_even_when_unavailable() {
        McpAvailabilityProbe unconfiguredProbe = new McpAvailabilityProbe(
                "", "/actuator/health", Duration.ofMillis(100));
        unconfiguredProbe.setAvailable(false);
        when(delegate.classify(any(), any(), any())).thenReturn(result(0.95f));
        McpCappingLlmService cap = new McpCappingLlmService(delegate, unconfiguredProbe, 0.7f);

        LlmResult got = cap.classify("blk", 1, "doc");

        assertThat(got.confidence()).isEqualTo(0.95f);
    }

    @Test
    void rationale_null_is_handled_when_capping() {
        probe.setAvailable(false);
        when(delegate.classify(any(), any(), any()))
                .thenReturn(new LlmResult(Map.of(), 0.9f, null,
                        LlmBackendId.ANTHROPIC, "model", 0L, 0L));
        McpCappingLlmService cap = new McpCappingLlmService(delegate, probe, 0.7f);

        LlmResult got = cap.classify("blk", 1, "doc");

        assertThat(got.confidence()).isEqualTo(0.7f);
        assertThat(got.rationale()).contains("MCP unreachable");
    }

    @Test
    void exceptions_pass_through() {
        probe.setAvailable(false);
        when(delegate.classify(any(), any(), any()))
                .thenThrow(new RuntimeException("upstream boom"));
        McpCappingLlmService cap = new McpCappingLlmService(delegate, probe, 0.7f);

        assertThatThrownBy(() -> cap.classify("blk", 1, "doc"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("upstream boom");
    }

    @Test
    void delegate_metadata_preserved_when_capping() {
        probe.setAvailable(false);
        LlmResult before = new LlmResult(
                Map.of("category", "HR > Letters"),
                0.92f,
                "matched HR taxonomy",
                LlmBackendId.OLLAMA,
                "qwen2.5:32b",
                123L, 456L);
        when(delegate.classify(any(), any(), any())).thenReturn(before);
        McpCappingLlmService cap = new McpCappingLlmService(delegate, probe, 0.7f);

        LlmResult got = cap.classify("blk", 1, "doc");

        assertThat(got.confidence()).isEqualTo(0.7f);
        assertThat(got.result()).containsEntry("category", "HR > Letters");
        assertThat(got.backend()).isEqualTo(LlmBackendId.OLLAMA);
        assertThat(got.modelId()).isEqualTo("qwen2.5:32b");
        assertThat(got.tokensIn()).isEqualTo(123L);
        assertThat(got.tokensOut()).isEqualTo(456L);
    }

    @Test
    void invalid_max_confidence_rejected() {
        assertThatThrownBy(() -> new McpCappingLlmService(delegate, probe, -0.1f))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new McpCappingLlmService(delegate, probe, 1.1f))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
