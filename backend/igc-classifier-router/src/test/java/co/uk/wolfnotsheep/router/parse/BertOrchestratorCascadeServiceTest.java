package co.uk.wolfnotsheep.router.parse;

import org.junit.jupiter.api.Test;

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

class BertOrchestratorCascadeServiceTest {

    private static RouterPolicy permissivePolicy() {
        // Accept threshold 0.0 = every result lands; existing tests
        // pre-date the policy reading so they were written for the
        // accept-everything case.
        return new RouterPolicy(
                new RouterPolicy.TierPolicy(true, 0.0f),
                new RouterPolicy.TierPolicy(true, 0.0f),
                new RouterPolicy.TierPolicy(true, 0.0f));
    }

    @Test
    void bert_classifier_block_returns_BERT_outcome_when_dispatcher_succeeds() {
        BertHttpDispatcher dispatcher = mock(BertHttpDispatcher.class);
        when(dispatcher.infer(anyString(), any(), any(), anyString()))
                .thenReturn(new BertInferenceResult("hr_letter", 0.93f, "2026.04.0"));
        CascadeService inner = new MockCascadeService();

        BertOrchestratorCascadeService orchestrator =
                new BertOrchestratorCascadeService(dispatcher, inner, () -> permissivePolicy());

        CascadeOutcome outcome = orchestrator.run("block-1", 3, "BERT_CLASSIFIER", "the doc");

        assertThat(outcome.tierOfDecision()).isEqualTo("BERT");
        assertThat(outcome.confidence())
                .isCloseTo(0.93f, org.assertj.core.data.Offset.offset(0.001f));
        assertThat(outcome.result()).containsEntry("label", "hr_letter");
        assertThat(outcome.result()).containsEntry("modelVersion", "2026.04.0");
        assertThat(outcome.trace()).hasSize(1);
        assertThat(outcome.trace().get(0).tier()).isEqualTo("BERT");
        assertThat(outcome.trace().get(0).accepted()).isTrue();
        assertThat(outcome.trace().get(0).errorCode()).isNull();
    }

    @Test
    void bert_classifier_block_falls_through_to_inner_on_MODEL_NOT_LOADED() {
        BertHttpDispatcher dispatcher = mock(BertHttpDispatcher.class);
        when(dispatcher.infer(anyString(), any(), any(), anyString()))
                .thenThrow(new BertTierFallthroughException(
                        "MODEL_NOT_LOADED", "no model loaded"));
        CascadeService inner = mock(CascadeService.class);
        when(inner.run(anyString(), anyInt(), anyString(), anyString()))
                .thenReturn(new CascadeOutcome(
                        "MOCK", 0.5f,
                        java.util.Map.of("label", "MOCK_LABEL"),
                        "stub", java.util.List.of(),
                        java.util.List.of(new CascadeOutcome.TraceStep(
                                "BERT", false, 0.0f, 0L, 0L, "MOCK_DISABLED")),
                        0L, 0L));

        BertOrchestratorCascadeService orchestrator =
                new BertOrchestratorCascadeService(dispatcher, inner);

        CascadeOutcome outcome = orchestrator.run("block-1", 3, "BERT_CLASSIFIER", "the doc");

        assertThat(outcome.tierOfDecision()).isEqualTo("MOCK");
        verify(inner, times(1)).run("block-1", 3, "BERT_CLASSIFIER", "the doc");

        // First trace step is the BERT-tier failure with the carried errorCode;
        // subsequent steps come from the inner cascade.
        assertThat(outcome.trace()).hasSizeGreaterThanOrEqualTo(2);
        assertThat(outcome.trace().get(0).tier()).isEqualTo("BERT");
        assertThat(outcome.trace().get(0).accepted()).isFalse();
        assertThat(outcome.trace().get(0).errorCode()).isEqualTo("MODEL_NOT_LOADED");
    }

    @Test
    void prompt_block_delegates_directly_to_inner_without_calling_dispatcher() {
        BertHttpDispatcher dispatcher = mock(BertHttpDispatcher.class);
        CascadeService inner = mock(CascadeService.class);
        when(inner.run(anyString(), any(), anyString(), anyString()))
                .thenReturn(new CascadeOutcome(
                        "LLM", 0.84f,
                        java.util.Map.of("category", "HR Letter"),
                        null, java.util.List.of(),
                        java.util.List.of(new CascadeOutcome.TraceStep(
                                "LLM", true, 0.84f, 100L, 1L, null)),
                        1L, 100L));

        BertOrchestratorCascadeService orchestrator =
                new BertOrchestratorCascadeService(dispatcher, inner);

        CascadeOutcome outcome = orchestrator.run("block-1", null, "PROMPT", "the doc");

        assertThat(outcome.tierOfDecision()).isEqualTo("LLM");
        verify(inner, times(1)).run("block-1", null, "PROMPT", "the doc");
        verify(dispatcher, never()).infer(anyString(), any(), any(), anyString());
    }

    @Test
    void null_block_type_delegates_to_inner_without_calling_dispatcher() {
        BertHttpDispatcher dispatcher = mock(BertHttpDispatcher.class);
        CascadeService inner = mock(CascadeService.class);
        when(inner.run(anyString(), any(), any(), anyString()))
                .thenReturn(new CascadeOutcome(
                        "MOCK", 0.5f, java.util.Map.of(),
                        null, java.util.List.of(), java.util.List.of(),
                        0L, 0L));

        BertOrchestratorCascadeService orchestrator =
                new BertOrchestratorCascadeService(dispatcher, inner);

        orchestrator.run("block-1", null, null, "the doc");

        verify(inner, times(1)).run("block-1", null, null, "the doc");
        verify(dispatcher, never()).infer(anyString(), any(), any(), anyString());
    }

    @Test
    void below_threshold_falls_through_to_inner_with_BELOW_THRESHOLD_code() {
        BertHttpDispatcher dispatcher = mock(BertHttpDispatcher.class);
        when(dispatcher.infer(anyString(), any(), any(), anyString()))
                .thenReturn(new BertInferenceResult("hr_letter", 0.6f, "2026.04.0"));
        CascadeService inner = mock(CascadeService.class);
        when(inner.run(anyString(), any(), anyString(), anyString()))
                .thenReturn(new CascadeOutcome(
                        "MOCK", 0.5f, java.util.Map.of("category", "MOCK_CATEGORY"),
                        null, java.util.List.of(), java.util.List.of(),
                        0L, 0L));

        // Strict policy: BERT must clear 0.92.
        RouterPolicy strict = new RouterPolicy(
                new RouterPolicy.TierPolicy(true, 0.92f),
                new RouterPolicy.TierPolicy(true, 0.0f),
                new RouterPolicy.TierPolicy(true, 0.0f));
        BertOrchestratorCascadeService orchestrator =
                new BertOrchestratorCascadeService(dispatcher, inner, () -> strict);

        CascadeOutcome outcome = orchestrator.run("block-1", 3, "BERT_CLASSIFIER", "the doc");

        // 0.6 below 0.92 → fall through to inner.
        assertThat(outcome.tierOfDecision()).isEqualTo("MOCK");
        assertThat(outcome.trace().get(0).tier()).isEqualTo("BERT");
        assertThat(outcome.trace().get(0).accepted()).isFalse();
        assertThat(outcome.trace().get(0).errorCode()).isEqualTo("BELOW_THRESHOLD");
        assertThat(outcome.trace().get(0).confidence()).isEqualTo(0.6f);
    }

    @Test
    void disabled_tier_skips_dispatch_entirely() {
        BertHttpDispatcher dispatcher = mock(BertHttpDispatcher.class);
        CascadeService inner = mock(CascadeService.class);
        when(inner.run(anyString(), any(), anyString(), anyString()))
                .thenReturn(new CascadeOutcome(
                        "MOCK", 0.5f, java.util.Map.of(),
                        null, java.util.List.of(), java.util.List.of(), 0L, 0L));

        // BERT disabled.
        RouterPolicy disabled = new RouterPolicy(
                new RouterPolicy.TierPolicy(false, 0.0f),
                new RouterPolicy.TierPolicy(true, 0.0f),
                new RouterPolicy.TierPolicy(true, 0.0f));
        BertOrchestratorCascadeService orchestrator =
                new BertOrchestratorCascadeService(dispatcher, inner, () -> disabled);

        CascadeOutcome outcome = orchestrator.run("block-1", 3, "BERT_CLASSIFIER", "x");

        verify(dispatcher, never()).infer(anyString(), any(), any(), anyString());
        assertThat(outcome.tierOfDecision()).isEqualTo("MOCK");
        assertThat(outcome.trace().get(0).errorCode()).isEqualTo("TIER_DISABLED");
    }

    @Test
    void BertBlockUnknownException_propagates_does_not_fall_through() {
        BertHttpDispatcher dispatcher = mock(BertHttpDispatcher.class);
        when(dispatcher.infer(anyString(), any(), any(), anyString()))
                .thenThrow(new BertBlockUnknownException("block not found"));
        CascadeService inner = mock(CascadeService.class);

        BertOrchestratorCascadeService orchestrator =
                new BertOrchestratorCascadeService(dispatcher, inner);

        assertThatThrownBy(() -> orchestrator.run("block-1", 3, "BERT_CLASSIFIER", "x"))
                .isInstanceOf(BertBlockUnknownException.class)
                .hasMessageContaining("block not found");
        verify(inner, never()).run(anyString(), any(), anyString(), anyString());
    }
}
