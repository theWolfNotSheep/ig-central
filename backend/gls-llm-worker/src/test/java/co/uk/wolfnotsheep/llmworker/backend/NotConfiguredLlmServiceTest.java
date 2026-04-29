package co.uk.wolfnotsheep.llmworker.backend;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotConfiguredLlmServiceTest {

    @Test
    void classify_always_throws_LlmNotConfiguredException() {
        NotConfiguredLlmService service = new NotConfiguredLlmService();
        assertThatThrownBy(() -> service.classify("block-1", null, "x"))
                .isInstanceOf(LlmNotConfiguredException.class)
                .hasMessageContaining("no LLM backend is configured");
    }

    @Test
    void activeBackend_is_NONE_and_isReady_is_false() {
        NotConfiguredLlmService service = new NotConfiguredLlmService();
        assertThat(service.activeBackend()).isEqualTo(LlmBackendId.NONE);
        assertThat(service.isReady()).isFalse();
    }
}
