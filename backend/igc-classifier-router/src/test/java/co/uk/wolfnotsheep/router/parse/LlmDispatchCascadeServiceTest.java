package co.uk.wolfnotsheep.router.parse;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class LlmDispatchCascadeServiceTest {

    @Test
    void run_dispatches_to_pipeline_exchange_and_returns_LLM_tier_on_success() {
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        LlmDispatchCascadeService service = new LlmDispatchCascadeService(
                rabbit, "igc.pipeline", "pipeline.llm.requested", Duration.ofSeconds(2));

        // When the dispatch is sent, fake the LLM worker by feeding
        // a successful completion back through the listener entry
        // point on a separate thread (simulates Rabbit delivering
        // the response while the main thread blocks on the future).
        doAnswer(invocation -> {
            LlmJobRequest req = invocation.getArgument(2);
            new Thread(() -> {
                try {
                    Thread.sleep(20);
                    service.completeTestOnly(new LlmJobResult(
                            req.jobId(), req.pipelineRunId(), req.nodeRunId(),
                            true, "result-1", "cat-1", "MOCK_CATEGORY",
                            "INTERNAL", java.util.List.of("tag-a"), 0.84,
                            false, "ret-1", java.util.List.of("policy-1"),
                            Map.of("key", "value"), null, null, Instant.now()));
                } catch (InterruptedException ignore) { /* test only */ }
            }).start();
            return null;
        }).when(rabbit).convertAndSend(eq("igc.pipeline"), eq("pipeline.llm.requested"),
                any(Object.class));

        CascadeOutcome outcome = service.run("block-1", 3, "PROMPT", "the document text");

        assertThat(outcome.tierOfDecision()).isEqualTo("LLM");
        assertThat(outcome.confidence()).isCloseTo(0.84f, org.assertj.core.data.Offset.offset(0.001f));
        assertThat(outcome.result()).containsEntry("category", "MOCK_CATEGORY");
        assertThat(outcome.result()).containsEntry("sensitivity", "INTERNAL");
        assertThat(outcome.result()).containsEntry("categoryId", "cat-1");
        assertThat(outcome.trace()).hasSize(1);
        assertThat(outcome.trace().get(0).tier()).isEqualTo("LLM");
        assertThat(outcome.trace().get(0).accepted()).isTrue();
        verify(rabbit, times(1)).convertAndSend(anyString(), anyString(), any(Object.class));
        assertThat(service.pendingCount()).isZero();
    }

    @Test
    void run_throws_LlmJobTimeoutException_when_no_response_within_window() {
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        LlmDispatchCascadeService service = new LlmDispatchCascadeService(
                rabbit, "igc.pipeline", "pipeline.llm.requested", Duration.ofMillis(50));

        // No response is fed; the service should time out cleanly.
        assertThatThrownBy(() -> service.run("block-1", null, "PROMPT", "x"))
                .isInstanceOf(LlmJobTimeoutException.class);
        assertThat(service.pendingCount()).isZero();
    }

    @Test
    void run_throws_LlmJobFailedException_when_worker_returns_failure() {
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        LlmDispatchCascadeService service = new LlmDispatchCascadeService(
                rabbit, "igc.pipeline", "pipeline.llm.requested", Duration.ofSeconds(2));

        doAnswer(invocation -> {
            LlmJobRequest req = invocation.getArgument(2);
            new Thread(() -> {
                try {
                    Thread.sleep(10);
                    service.completeTestOnly(new LlmJobResult(
                            req.jobId(), req.pipelineRunId(), req.nodeRunId(),
                            false, null, null, null, null, null, null, null, null, null,
                            null, null, "anthropic returned 500", Instant.now()));
                } catch (InterruptedException ignore) { /* test only */ }
            }).start();
            return null;
        }).when(rabbit).convertAndSend(anyString(), anyString(), any(Object.class));

        assertThatThrownBy(() -> service.run("block-1", null, "PROMPT", "x"))
                .isInstanceOf(LlmJobFailedException.class)
                .hasMessageContaining("anthropic returned 500");
    }

    @Test
    void run_propagates_AmqpException_as_UncheckedIOException() {
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        doThrow(new AmqpException("broker unreachable"))
                .when(rabbit).convertAndSend(anyString(), anyString(), any(Object.class));
        LlmDispatchCascadeService service = new LlmDispatchCascadeService(
                rabbit, "igc.pipeline", "pipeline.llm.requested", Duration.ofSeconds(2));

        assertThatThrownBy(() -> service.run("block-1", null, "PROMPT", "x"))
                .isInstanceOf(java.io.UncheckedIOException.class)
                .hasMessageContaining("LLM dispatch failed");
        assertThat(service.pendingCount()).isZero();
    }

    @Test
    void onLlmJobCompleted_drops_orphan_completions_quietly() {
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        LlmDispatchCascadeService service = new LlmDispatchCascadeService(
                rabbit, "igc.pipeline", "pipeline.llm.requested", Duration.ofSeconds(2));

        // No future is registered for this jobId — should be a no-op.
        service.completeTestOnly(new LlmJobResult(
                "orphan-id", null, null, true, null, null, null, null, null, 1.0,
                null, null, null, null, null, null, Instant.now()));
        assertThat(service.pendingCount()).isZero();
    }

    @Test
    void onLlmJobCompleted_drops_null_payload_quietly() {
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        LlmDispatchCascadeService service = new LlmDispatchCascadeService(
                rabbit, "igc.pipeline", "pipeline.llm.requested", Duration.ofSeconds(2));
        service.completeTestOnly(null);
        assertThat(service.pendingCount()).isZero();
    }
}
