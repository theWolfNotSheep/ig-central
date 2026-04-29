package co.uk.wolfnotsheep.extraction.audio.parse;

import java.io.InputStream;

/**
 * Strategy interface for the transcription backend. CSV #46 ships
 * two impls: {@link OpenAiWhisperService} (cloud) and
 * {@link NotConfiguredAudioTranscriptionService} (default fallback).
 *
 * <p>Implementations receive the raw source stream + a content-length
 * hint; backends that need a file (most do) materialise to a temp
 * file. Implementations are responsible for closing the source
 * stream.
 */
public interface AudioTranscriptionService {

    String providerId();

    /**
     * @param input        source audio bytes; impl closes.
     * @param fileName     filename hint (extension informs the backend's
     *                     mime detect when the bytes are ambiguous).
     * @param size         content length in bytes if known, else -1.
     * @param language     ISO 639-1 hint, or null for auto-detect.
     * @param prompt       optional prompt to bias transcription.
     * @return populated {@link AudioResult}.
     * @throws AudioCorruptException if the backend can't parse the bytes.
     * @throws AudioNotConfiguredException if no provider is wired.
     * @throws java.io.UncheckedIOException for backend I/O / network failures.
     */
    AudioResult transcribe(InputStream input, String fileName, long size,
                           String language, String prompt);

    boolean isReady();
}
