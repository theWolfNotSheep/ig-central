package co.uk.wolfnotsheep.extraction.audio.parse;

public class AudioCorruptException extends RuntimeException {
    public AudioCorruptException(String message) {
        super(message);
    }

    public AudioCorruptException(String message, Throwable cause) {
        super(message, cause);
    }
}
