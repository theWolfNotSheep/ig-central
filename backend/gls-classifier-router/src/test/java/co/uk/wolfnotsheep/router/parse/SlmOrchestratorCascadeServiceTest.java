package co.uk.wolfnotsheep.router.parse;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SlmOrchestratorCascadeServiceTest {

    private static RouterPolicy permissivePolicy() {
        return new RouterPolicy(
                new RouterPolicy.TierPolicy(true, 0.0f),
                new RouterPolicy.TierPolicy(true, 0.0f),
                new RouterPolicy.TierPolicy(true, 0.0f));
    }

    @Test
    void prompt_block_returns_SLM_outcome_when_dispatcher_succeeds() {
        SlmHttpDispatcher dispatcher = mock(SlmHttpDispatcher.class);
        when(dispatcher.classify(anyString(), any(), any(), anyString()))
                .thenReturn(new SlmInferenceResult(
                        Map.of("category", "HR Letter", "sensitivity", "INTERNAL"),
                        0.81f, "ANTHROPIC_HAIKU", "claude-haiku-4-5",
                        320L, 40L, 1L));
        CascadeService inner = new MockCascadeService();

        SlmOrchestratorCascadeService orchestrator =
                new SlmOrchestratorCascadeService(dispatcher, inner, () -> permissivePolicy());

        CascadeOutcome outcome = orchestrator.run("block-1", 3, "PROMPT", "the doc");

        assertThat(outcome.tierOfDecision()).isEqualTo("SLM");
        assertThat(outcome.confidence())
                .isCloseTo(0.81f, org.assertj.core.data.Offset.offset(0.001f));
        assertThat(outcome.result()).containsEntry("category", "HR Letter");
        assertThat(outcome.result()).containsEntry("backend", "ANTHROPIC_HAIKU");
        assertThat(outcome.result()).containsEntry("modelId", "claude-haiku-4-5");
        assertThat(outcome.costUnits()).isEqualTo(1L);
        assertThat(outcome.trace()).hasSize(1);
        assertThat(outcome.trace().get(0).tier()).isEqualTo("SLM");
        assertThat(outcome.trace().get(0).accepted()).isTrue();
    }

    @Test
    void prompt_block_falls_through_to_inner_on_SLM_NOT_CONFIGURED() {
        SlmHttpDispatcher dispatcher = mock(SlmHttpDispatcher.class);
        when(dispatcher.classify(anyString(), any(), any(), anyString()))
                .thenThrow(new SlmTierFallthroughException(
                        "SLM_NOT_CONFIGURED", "no backend wired"));
        CascadeService inner = mock(CascadeService.class);
        when(inner.run(anyString(), anyInt(), anyString(), anyString()))
                .thenReturn(new CascadeOutcome(
                        "LLM", 0.84f,
                        Map.of("category", "HR Letter"),
                        null, java.util.List.of(),
                        java.util.List.of(new CascadeOutcome.TraceStep(
                                "LLM", true, 0.84f, 100L, 1L, null)),
                        1L, 100L));

        SlmOrchestratorCascadeService orchestrator =
                new SlmOrchestratorCascadeService(dispatcher, inner);

        CascadeOutcome outcome = orchestrator.run("block-1", 3, "PROMPT", "the doc");

        assertThat(outcome.tierOfDecision()).isEqualTo("LLM");
        verify(inner, times(1)).run("block-1", 3, "PROMPT", "the doc");

        // SLM step prepended to inner trace
        assertThat(outcome.trace()).hasSizeGreaterThanOrEqualTo(2);
        assertThat(outcome.trace().get(0).tier()).isEqualTo("SLM");
        assertThat(outcome.trace().get(0).accepted()).isFalse();
        assertThat(outcome.trace().get(0).errorCode()).isEqualTo("SLM_NOT_CONFIGURED");
    }

    @Test
    void bert_classifier_block_delegates_directly_to_inner() {
        SlmHttpDispatcher dispatcher = mock(SlmHttpDispatcher.class);
        CascadeService inner = mock(CascadeService.class);
        when(inner.run(anyString(), any(), anyString(), anyString()))
                .thenReturn(new CascadeOutcome(
                        "BERT", 0.93f, Map.of("label", "hr_letter"),
                        null, java.util.List.of(), java.util.List.of(),
                        1L, 100L));

        SlmOrchestratorCascadeService orchestrator =
                new SlmOrchestratorCascadeService(dispatcher, inner);

        CascadeOutcome outcome = orchestrator.run("block-1", null, "BERT_CLASSIFIER", "the doc");

        assertThat(outcome.tierOfDecision()).isEqualTo("BERT");
        verify(inner, times(1)).run("block-1", null, "BERT_CLASSIFIER", "the doc");
        verify(dispatcher, never()).classify(anyString(), any(), any(), anyString());
    }

    @Test
    void null_block_type_delegates_to_inner_without_calling_dispatcher() {
        SlmHttpDispatcher dispatcher = mock(SlmHttpDispatcher.class);
        CascadeService inner = mock(CascadeService.class);
        when(inner.run(anyString(), any(), any(), anyString()))
                .thenReturn(new CascadeOutcome(
                        "MOCK", 0.5f, Map.of(),
                        null, java.util.List.of(), java.util.List.of(),
                        0L, 0L));

        SlmOrchestratorCascadeService orchestrator =
                new SlmOrchestratorCascadeService(dispatcher, inner);

        orchestrator.run("block-1", null, null, "the doc");

        verify(inner, times(1)).run("block-1", null, null, "the doc");
        verify(dispatcher, never()).classify(anyString(), any(), any(), anyString());
    }

    @Test
    void below_threshold_falls_through_to_inner_with_BELOW_THRESHOLD_code() {
        SlmHttpDispatcher dispatcher = mock(SlmHttpDispatcher.class);
        when(dispatcher.classify(anyString(), any(), any(), anyString()))
                .thenReturn(new SlmInferenceResult(
                        Map.of("category", "HR"), 0.5f, "ANTHROPIC_HAIKU",
                        "claude-haiku-4-5", 100L, 20L, 1L));
        CascadeService inner = mock(CascadeService.class);
        when(inner.run(anyString(), any(), anyString(), anyString()))
                .thenReturn(new CascadeOutcome(
                        "LLM", 0.9f, Map.of("category", "HR Letter"),
                        null, java.util.List.of(), java.util.List.of(), 1L, 100L));

        // Strict policy: SLM must clear 0.85.
        RouterPolicy strict = new RouterPolicy(
                new RouterPolicy.TierPolicy(true, 0.0f),
                new RouterPolicy.TierPolicy(true, 0.85f),
                new RouterPolicy.TierPolicy(true, 0.0f));
        SlmOrchestratorCascadeService orchestrator =
                new SlmOrchestratorCascadeService(dispatcher, inner, () -> strict);

        CascadeOutcome outcome = orchestrator.run("block-1", 3, "PROMPT", "the doc");

        assertThat(outcome.tierOfDecision()).isEqualTo("LLM");
        assertThat(outcome.trace().get(0).tier()).isEqualTo("SLM");
        assertThat(outcome.trace().get(0).accepted()).isFalse();
        assertThat(outcome.trace().get(0).errorCode()).isEqualTo("BELOW_THRESHOLD");
    }

    @Test
    void disabled_tier_skips_dispatch_entirely() {
        SlmHttpDispatcher dispatcher = mock(SlmHttpDispatcher.class);
        CascadeService inner = mock(CascadeService.class);
        when(inner.run(anyString(), any(), anyString(), anyString()))
                .thenReturn(new CascadeOutcome(
                        "MOCK", 0.5f, Map.of(), null,
                        java.util.List.of(), java.util.List.of(), 0L, 0L));

        RouterPolicy disabled = new RouterPolicy(
                new RouterPolicy.TierPolicy(true, 0.0f),
                new RouterPolicy.TierPolicy(false, 0.0f),
                new RouterPolicy.TierPolicy(true, 0.0f));
        SlmOrchestratorCascadeService orchestrator =
                new SlmOrchestratorCascadeService(dispatcher, inner, () -> disabled);

        CascadeOutcome outcome = orchestrator.run("block-1", 3, "PROMPT", "x");

        verify(dispatcher, never()).classify(anyString(), any(), any(), anyString());
        assertThat(outcome.tierOfDecision()).isEqualTo("MOCK");
        assertThat(outcome.trace().get(0).errorCode()).isEqualTo("TIER_DISABLED");
    }

    @Test
    void SlmBlockUnknownException_propagates_does_not_fall_through() {
        SlmHttpDispatcher dispatcher = mock(SlmHttpDispatcher.class);
        when(dispatcher.classify(anyString(), any(), any(), anyString()))
                .thenThrow(new SlmBlockUnknownException("block not found"));
        CascadeService inner = mock(CascadeService.class);

        SlmOrchestratorCascadeService orchestrator =
                new SlmOrchestratorCascadeService(dispatcher, inner, () -> permissivePolicy());

        assertThatThrownBy(() -> orchestrator.run("block-1", 3, "PROMPT", "x"))
                .isInstanceOf(SlmBlockUnknownException.class)
                .hasMessageContaining("block not found");
        verify(inner, never()).run(anyString(), any(), anyString(), anyString());
    }

    @Test
    void composes_with_BERT_orchestrator_outermost() {
        // Sanity: BERT(SLM(LLM)) — the production composition order.
        // BERT misses (fallthrough) → SLM serves PROMPT → SLM result wins.
        BertHttpDispatcher bertDispatcher = mock(BertHttpDispatcher.class);
        when(bertDispatcher.infer(anyString(), any(), any(), anyString()))
                .thenThrow(new BertTierFallthroughException(
                        "MODEL_NOT_LOADED", "no model"));
        SlmHttpDispatcher slmDispatcher = mock(SlmHttpDispatcher.class);
        when(slmDispatcher.classify(anyString(), any(), any(), anyString()))
                .thenReturn(new SlmInferenceResult(
                        Map.of("category", "HR"), 0.8f, "ANTHROPIC_HAIKU",
                        "claude-haiku-4-5", 100L, 20L, 1L));

        java.util.function.Supplier<RouterPolicy> policy = () -> permissivePolicy();
        // BERT_CLASSIFIER block: BERT tier tries (fallthrough), then
        // SLM bypasses (BERT_CLASSIFIER not handled by SLM), inner is
        // the mock.
        CascadeService chain = new BertOrchestratorCascadeService(
                bertDispatcher,
                new SlmOrchestratorCascadeService(slmDispatcher, new MockCascadeService(), policy),
                policy);

        CascadeOutcome bertCall = chain.run("block-1", 3, "BERT_CLASSIFIER", "the doc");
        assertThat(bertCall.tierOfDecision()).isEqualTo("MOCK");

        // PROMPT block: BERT tier bypasses (PROMPT not handled), SLM
        // serves it.
        CascadeOutcome promptCall = chain.run("block-2", 1, "PROMPT", "the doc");
        assertThat(promptCall.tierOfDecision()).isEqualTo("SLM");
        assertThat(promptCall.result()).containsEntry("category", "HR");
    }
}
