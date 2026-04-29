package co.uk.wolfnotsheep.router.parse;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LlmHttpCascadeServiceTest {

    @Test
    void returns_LLM_outcome_on_success() {
        LlmHttpDispatcher dispatcher = mock(LlmHttpDispatcher.class);
        when(dispatcher.classify(anyString(), any(), any(), anyString()))
                .thenReturn(new LlmInferenceResult(
                        Map.of("category", "HR Letter", "sensitivity", "INTERNAL"),
                        0.92f, "claude-sonnet-4-5", 800L, 120L, 1L));

        LlmHttpCascadeService service = new LlmHttpCascadeService(dispatcher);
        CascadeOutcome outcome = service.run("block-1", 3, "PROMPT", "the doc");

        assertThat(outcome.tierOfDecision()).isEqualTo("LLM");
        assertThat(outcome.confidence()).isEqualTo(0.92f);
        assertThat(outcome.result()).containsEntry("category", "HR Letter");
        assertThat(outcome.result()).containsEntry("modelId", "claude-sonnet-4-5");
        assertThat(outcome.costUnits()).isEqualTo(1L);
        assertThat(outcome.trace()).hasSize(1);
        assertThat(outcome.trace().get(0).tier()).isEqualTo("LLM");
        assertThat(outcome.trace().get(0).accepted()).isTrue();
    }

    @Test
    void fallthrough_translates_to_LlmJobFailedException() {
        // The LLM tier is the cascade's floor — a fallthrough here
        // means there's nothing to fall back to, so it surfaces as
        // ROUTER_LLM_FAILED 502 (mirrors the legacy Rabbit failure
        // mapping).
        LlmHttpDispatcher dispatcher = mock(LlmHttpDispatcher.class);
        when(dispatcher.classify(anyString(), any(), any(), anyString()))
                .thenThrow(new LlmTierFallthroughException(
                        "LLM_NOT_CONFIGURED", "no backend wired"));

        LlmHttpCascadeService service = new LlmHttpCascadeService(dispatcher);

        assertThatThrownBy(() -> service.run("block-1", 3, "PROMPT", "x"))
                .isInstanceOf(LlmJobFailedException.class)
                .hasMessageContaining("LLM_NOT_CONFIGURED");
    }

    @Test
    void LlmBlockUnknownException_propagates_for_422_mapping() {
        LlmHttpDispatcher dispatcher = mock(LlmHttpDispatcher.class);
        when(dispatcher.classify(anyString(), any(), any(), anyString()))
                .thenThrow(new LlmBlockUnknownException("block not found"));

        LlmHttpCascadeService service = new LlmHttpCascadeService(dispatcher);

        assertThatThrownBy(() -> service.run("block-1", 3, "PROMPT", "x"))
                .isInstanceOf(LlmBlockUnknownException.class);
    }
}
