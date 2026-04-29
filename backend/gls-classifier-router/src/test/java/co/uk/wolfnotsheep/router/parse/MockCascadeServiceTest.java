package co.uk.wolfnotsheep.router.parse;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MockCascadeServiceTest {

    private final MockCascadeService service = new MockCascadeService();

    @Test
    void prompt_block_yields_classification_shape() {
        CascadeOutcome outcome = service.run("block-1", null, "PROMPT", "some text");

        assertThat(outcome.tierOfDecision()).isEqualTo("MOCK");
        assertThat(outcome.confidence()).isEqualTo(0.5f);
        assertThat(outcome.result()).containsEntry("category", "MOCK_CATEGORY");
        assertThat(outcome.result()).containsEntry("sensitivity", "INTERNAL");
        assertThat(outcome.result()).containsEntry("confidence", 0.5f);
        assertThat(outcome.byteCount()).isEqualTo(9);
    }

    @Test
    void bert_classifier_block_yields_label_shape() {
        CascadeOutcome outcome = service.run("block-1", null, "BERT_CLASSIFIER", "x");

        assertThat(outcome.result()).containsEntry("label", "MOCK_LABEL");
        assertThat(outcome.result()).containsEntry("confidence", 0.5f);
        assertThat(outcome.result()).doesNotContainKey("category");
    }

    @Test
    void null_block_type_defaults_to_classification_shape() {
        CascadeOutcome outcome = service.run("block-1", null, null, "x");
        assertThat(outcome.result()).containsKey("category");
    }

    @Test
    void empty_text_yields_zero_byte_count() {
        CascadeOutcome outcome = service.run("block-1", null, "PROMPT", "");
        assertThat(outcome.byteCount()).isZero();
    }

    @Test
    void null_text_yields_zero_byte_count() {
        CascadeOutcome outcome = service.run("block-1", null, "PROMPT", null);
        assertThat(outcome.byteCount()).isZero();
    }

    @Test
    void trace_advertises_tier_disabled_status() {
        CascadeOutcome outcome = service.run("block-1", null, "PROMPT", "x");
        assertThat(outcome.trace()).hasSize(1);
        assertThat(outcome.trace().get(0).errorCode()).isEqualTo("MOCK_DISABLED");
    }
}
