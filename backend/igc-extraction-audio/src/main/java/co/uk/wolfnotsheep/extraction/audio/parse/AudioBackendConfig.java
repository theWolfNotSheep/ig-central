package co.uk.wolfnotsheep.extraction.audio.parse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Selects the {@link AudioTranscriptionService} bean based on
 * {@code igc.extraction.audio.provider}. Default {@code none} → the
 * not-configured fallback. {@code openai} → the OpenAI Whisper impl,
 * which itself falls back to not-configured if the API key is
 * missing or blank (so a misconfigured deploy still boots; it just
 * returns 503 on requests).
 */
@Configuration
public class AudioBackendConfig {

    private static final Logger log = LoggerFactory.getLogger(AudioBackendConfig.class);

    @Bean
    @ConditionalOnMissingBean
    public AudioTranscriptionService audioTranscriptionService(
            @Value("${igc.extraction.audio.provider:none}") String provider,
            @Value("${igc.extraction.audio.openai.api-key:}") String apiKey,
            @Value("${igc.extraction.audio.openai.endpoint:https://api.openai.com/v1/audio/transcriptions}") String endpoint,
            @Value("${igc.extraction.audio.openai.model:whisper-1}") String model,
            @Value("${igc.extraction.audio.openai.timeout:PT10M}") Duration timeout) {

        if (!"openai".equalsIgnoreCase(provider)) {
            log.info("audio: provider=none — returning AUDIO_NOT_CONFIGURED on requests");
            return new NotConfiguredAudioTranscriptionService();
        }
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("audio: provider=openai but OPENAI_API_KEY is empty — falling back to not-configured");
            return new NotConfiguredAudioTranscriptionService();
        }
        log.info("audio: provider=openai-whisper, endpoint={}, model={}", endpoint, model);
        return new OpenAiWhisperService(apiKey, endpoint, model, timeout);
    }
}
