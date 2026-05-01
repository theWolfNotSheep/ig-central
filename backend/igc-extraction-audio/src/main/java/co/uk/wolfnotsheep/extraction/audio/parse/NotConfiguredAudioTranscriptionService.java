package co.uk.wolfnotsheep.extraction.audio.parse;

import java.io.InputStream;

/**
 * Default backend when no real provider is wired. Always throws
 * {@link AudioNotConfiguredException}, which the controller maps to
 * RFC 7807 503 / {@code AUDIO_NOT_CONFIGURED}. Set
 * {@code igc.extraction.audio.provider=openai} (and provide
 * {@code OPENAI_API_KEY}) to swap in {@link OpenAiWhisperService}.
 */
public class NotConfiguredAudioTranscriptionService implements AudioTranscriptionService {

    @Override
    public String providerId() {
        return "none";
    }

    @Override
    public AudioResult transcribe(InputStream input, String fileName, long size,
                                  String language, String prompt) {
        try (input) {
            input.transferTo(java.io.OutputStream.nullOutputStream());
        } catch (java.io.IOException ignore) {
            // Drain failures aren't useful here — we're failing the
            // request anyway; just don't leave a half-read stream.
        }
        throw new AudioNotConfiguredException(
                "no audio transcription backend configured. "
                        + "Set igc.extraction.audio.provider=openai with OPENAI_API_KEY.");
    }

    @Override
    public boolean isReady() {
        return false;
    }
}
