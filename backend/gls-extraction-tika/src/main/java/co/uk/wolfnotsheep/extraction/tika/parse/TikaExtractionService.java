package co.uk.wolfnotsheep.extraction.tika.parse;

import io.micrometer.observation.annotation.Observed;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.apache.tika.exception.ZeroByteFileException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/**
 * Runs Apache Tika against a single document stream. Stateless and
 * thread-safe — Tika 3.x's {@link Tika} façade is itself thread-safe;
 * this class is a thin wrapper that drives the consistent metadata +
 * page-count + truncation handling our contract requires.
 *
 * <p>Failure semantics:
 *
 * <ul>
 *     <li>{@link TikaException} → {@link UnparseableDocumentException}
 *         — the bytes don't represent a recognisable document, or the
 *         parser refused to handle them. Maps to a 422 in the controller.</li>
 *     <li>{@link IOException} → {@link UncheckedIOException} — propagates
 *         to the controller for 5xx handling.</li>
 *     <li>OOM during parse → not caught here. The controller / process
 *         observability is the right boundary for that.</li>
 * </ul>
 */
@Service
public class TikaExtractionService {

    /** Default character ceiling. Aligns with the existing
     * {@code TextExtractionService} default in {@code gls-document-processing}
     * to keep extraction footprints comparable across services. */
    public static final int DEFAULT_MAX_CHARACTERS = 500_000;

    private final Tika tika = new Tika();
    private final int maxCharacters;

    public TikaExtractionService() {
        this(DEFAULT_MAX_CHARACTERS);
    }

    /** Spring constructor — value populated from
     * {@code gls.extraction.tika.max-characters} when present, else
     * {@link #DEFAULT_MAX_CHARACTERS}. */
    public TikaExtractionService(@Value("${gls.extraction.tika.max-characters:500000}") int maxCharacters) {
        if (maxCharacters <= 0) {
            throw new IllegalArgumentException("maxCharacters must be > 0");
        }
        this.maxCharacters = maxCharacters;
    }

    /**
     * Extract text from {@code input}. Reads the stream fully — caller
     * is responsible for closing it.
     *
     * @param input    the raw document bytes.
     * @param fileName a filename hint (extension informs Tika's
     *                 dispatch when content sniffing is ambiguous).
     *                 May be null.
     * @return populated {@link ExtractedText}.
     */
    @Observed(name = "tika.parse",
            contextualName = "tika-parse",
            lowCardinalityKeyValues = {"component", "tika"})
    public ExtractedText extract(InputStream input, String fileName) {
        Metadata metadata = new Metadata();
        if (fileName != null && !fileName.isBlank()) {
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, fileName);
        }
        CountingInputStream counting = new CountingInputStream(input);
        String text;
        try {
            text = tika.parseToString(counting, metadata, maxCharacters);
        } catch (ZeroByteFileException e) {
            // Empty input is semantically valid — caller asked us to
            // extract a zero-byte document, we return zero bytes of text.
            // Tika treats this as an exception; the contract treats it
            // as a successful no-op.
            text = "";
        } catch (TikaException e) {
            throw new UnparseableDocumentException(
                    "Tika could not parse the document: " + e.getMessage(), e);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read input during Tika parse", e);
        }
        boolean truncated = text.length() == maxCharacters;
        Integer pageCount = parsePageCount(metadata);
        String mimeType = metadata.get("Content-Type");
        return new ExtractedText(
                text,
                mimeType,
                pageCount,
                counting.bytesRead(),
                truncated);
    }

    private static Integer parsePageCount(Metadata metadata) {
        for (String key : new String[]{"xmpTPg:NPages", "meta:page-count", "Page-Count"}) {
            String raw = metadata.get(key);
            if (raw == null) continue;
            try {
                return Integer.parseInt(raw);
            } catch (NumberFormatException ignore) {
                // try next key
            }
        }
        return null;
    }

    /**
     * Decorating stream that counts bytes pulled by Tika without
     * holding any of them in memory — keeps {@code byteCount} accurate
     * even when the parser short-circuits at the character ceiling.
     */
    private static final class CountingInputStream extends InputStream {
        private final InputStream delegate;
        private long count;

        CountingInputStream(InputStream delegate) {
            this.delegate = delegate;
        }

        long bytesRead() {
            return count;
        }

        @Override
        public int read() throws IOException {
            int b = delegate.read();
            if (b >= 0) count++;
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int n = delegate.read(b, off, len);
            if (n > 0) count += n;
            return n;
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }
    }
}
