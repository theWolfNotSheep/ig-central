package co.uk.wolfnotsheep.bert.inference;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotLoadedInferenceEngineTest {

    private final NotLoadedInferenceEngine engine = new NotLoadedInferenceEngine();

    @Test
    void infer_throws_ModelNotLoadedException() {
        assertThatThrownBy(() -> engine.infer("block-1", null, "text"))
                .isInstanceOf(ModelNotLoadedException.class)
                .hasMessageContaining("no BERT model is loaded");
    }

    @Test
    void is_ready_returns_false() {
        assertThat(engine.isReady()).isFalse();
    }
}
