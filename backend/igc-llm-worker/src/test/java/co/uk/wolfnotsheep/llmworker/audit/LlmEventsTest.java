package co.uk.wolfnotsheep.llmworker.audit;

import co.uk.wolfnotsheep.platformaudit.envelope.AuditEvent;
import co.uk.wolfnotsheep.platformaudit.envelope.Outcome;
import co.uk.wolfnotsheep.platformaudit.envelope.Tier;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LlmEventsTest {

    @Test
    void completed_envelope_is_Tier_2_CLASSIFY_with_metadata() {
        AuditEvent event = LlmEvents.completed(
                "igc-llm-worker", "0.0.1", "instance-1",
                "node-1", "00-trace-span-01",
                "block-1", 3,
                "ANTHROPIC", "claude-sonnet-4-5", 0.81f,
                /* byteCount */ 1024, /* tokensIn */ 500, /* tokensOut */ 80,
                /* durationMs */ 200);

        assertThat(event.eventType()).isEqualTo("LLM_COMPLETED");
        assertThat(event.tier()).isEqualTo(Tier.SYSTEM);
        assertThat(event.action()).isEqualTo("CLASSIFY");
        assertThat(event.outcome()).isEqualTo(Outcome.SUCCESS);
        assertThat(event.nodeRunId()).isEqualTo("node-1");
        assertThat(event.details().metadata())
                .containsEntry("blockId", "block-1")
                .containsEntry("blockVersion", 3)
                .containsEntry("backend", "ANTHROPIC")
                .containsEntry("modelId", "claude-sonnet-4-5")
                .containsEntry("tokensIn", 500L)
                .containsEntry("tokensOut", 80L);
    }

    @Test
    void failed_envelope_carries_errorCode_and_errorMessage() {
        AuditEvent event = LlmEvents.failed(
                "igc-llm-worker", "0.0.1", "instance-1",
                "node-2", "00-trace-span-01",
                "LLM_NOT_CONFIGURED", "no backend wired");

        assertThat(event.eventType()).isEqualTo("LLM_FAILED");
        assertThat(event.outcome()).isEqualTo(Outcome.FAILURE);
        assertThat(event.details().metadata()).containsEntry("errorCode", "LLM_NOT_CONFIGURED");
        assertThat(event.details().content()).containsEntry("errorMessage", "no backend wired");
    }

    @Test
    void completed_omits_null_blockId_blockVersion_modelId_from_metadata() {
        AuditEvent event = LlmEvents.completed(
                "svc", "v", "i", "node", "tp",
                /* blockId */ null, /* blockVersion */ null,
                "OLLAMA", /* modelId */ null, 0.5f,
                100, 0, 0, 50);

        assertThat(event.details().metadata()).doesNotContainKeys("blockId", "blockVersion", "modelId");
        assertThat(event.details().metadata()).containsEntry("backend", "OLLAMA");
    }

    @Test
    void eventId_is_26_chars_uppercase() {
        AuditEvent event = LlmEvents.completed(
                "svc", "v", "i", null, null, null, null,
                "OLLAMA", null, 0.0f, 0, 0, 0, 0);
        assertThat(event.eventId()).hasSize(26);
        assertThat(event.eventId()).matches("^[0-9A-Z]+$");
    }
}
