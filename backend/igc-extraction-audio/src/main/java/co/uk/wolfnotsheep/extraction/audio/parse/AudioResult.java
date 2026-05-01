package co.uk.wolfnotsheep.extraction.audio.parse;

/**
 * Result of one transcription. {@code language} is the detected /
 * echoed language code; {@code durationSeconds} is the audio's wall
 * length when known; {@code byteCount} is source bytes consumed
 * (drives `costUnits` per CSV #22).
 */
public record AudioResult(
        String text,
        String detectedMimeType,
        String language,
        Float durationSeconds,
        long byteCount,
        String provider
) {
}
