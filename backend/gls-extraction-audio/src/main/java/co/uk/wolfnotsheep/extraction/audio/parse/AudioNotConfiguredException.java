package co.uk.wolfnotsheep.extraction.audio.parse;

/**
 * No transcription backend is configured. Mapped to RFC 7807
 * {@code AUDIO_NOT_CONFIGURED} / HTTP 503 — the service is reachable
 * but can't transcribe until a provider is wired.
 */
public class AudioNotConfiguredException extends RuntimeException {
    public AudioNotConfiguredException(String message) {
        super(message);
    }
}
