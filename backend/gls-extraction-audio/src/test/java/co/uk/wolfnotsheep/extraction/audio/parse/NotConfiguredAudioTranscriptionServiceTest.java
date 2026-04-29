package co.uk.wolfnotsheep.extraction.audio.parse;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotConfiguredAudioTranscriptionServiceTest {

    private final NotConfiguredAudioTranscriptionService backend = new NotConfiguredAudioTranscriptionService();

    @Test
    void provider_id_is_none() {
        assertThat(backend.providerId()).isEqualTo("none");
    }

    @Test
    void is_ready_is_false() {
        assertThat(backend.isReady()).isFalse();
    }

    @Test
    void transcribe_throws_AudioNotConfiguredException() {
        assertThatThrownBy(() -> backend.transcribe(
                new ByteArrayInputStream("audio".getBytes(StandardCharsets.UTF_8)),
                "x.mp3", 5L, "en", null))
                .isInstanceOf(AudioNotConfiguredException.class)
                .hasMessageContaining("no audio transcription backend configured");
    }
}
