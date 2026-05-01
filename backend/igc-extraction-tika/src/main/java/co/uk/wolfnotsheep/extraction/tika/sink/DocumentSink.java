package co.uk.wolfnotsheep.extraction.tika.sink;

/**
 * Receives extracted text that exceeds the inline byte ceiling and
 * returns a {@link ExtractedTextRef} the controller surfaces on the
 * contract's {@code textRef} response branch (CSV #19). Decoupled from
 * the MinIO SDK so the controller is unit-testable against fakes.
 */
public interface DocumentSink {

    /**
     * Upload {@code text} as a UTF-8 object and return a reference.
     * Implementations choose a deterministic key derived from
     * {@code nodeRunId} so a retried extraction overwrites the same
     * object rather than creating an orphan — keeps the storage tidy
     * without needing a separate cleanup pass.
     *
     * @throws java.io.UncheckedIOException on storage failure.
     */
    ExtractedTextRef upload(String nodeRunId, String text);
}
