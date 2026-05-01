package co.uk.wolfnotsheep.slm.backend;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotConfiguredSlmServiceTest {

    @Test
    void classify_always_throws_SlmNotConfiguredException() {
        NotConfiguredSlmService service = new NotConfiguredSlmService();
        assertThatThrownBy(() -> service.classify("block-1", null, "x"))
                .isInstanceOf(SlmNotConfiguredException.class)
                .hasMessageContaining("no SLM backend is configured");
    }

    @Test
    void activeBackend_is_NONE_and_isReady_is_false() {
        NotConfiguredSlmService service = new NotConfiguredSlmService();
        assertThat(service.activeBackend()).isEqualTo(SlmBackendId.NONE);
        assertThat(service.isReady()).isFalse();
    }
}
