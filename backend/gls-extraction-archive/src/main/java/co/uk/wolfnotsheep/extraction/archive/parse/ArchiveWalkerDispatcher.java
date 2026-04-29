package co.uk.wolfnotsheep.extraction.archive.parse;

import io.micrometer.observation.annotation.Observed;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Detects the archive's mime type via Tika and dispatches to the
 * matching {@link ArchiveWalker}. Holds the mapping from
 * {@link ArchiveType} to walker; throws
 * {@link UnsupportedArchiveTypeException} when no walker matches.
 *
 * <p>The detection step buffers the first few KB of the input so Tika
 * can sniff magic bytes without consuming the stream. The buffered
 * stream is then handed to the walker — keeps detection + walk on
 * a single pass.
 */
@Service
public class ArchiveWalkerDispatcher {

    private final Tika tika = new Tika();
    private final Map<ArchiveType, ArchiveWalker> walkers;

    public ArchiveWalkerDispatcher(List<ArchiveWalker> registered) {
        Map<ArchiveType, ArchiveWalker> map = new EnumMap<>(ArchiveType.class);
        for (ArchiveWalker w : registered) {
            map.put(w.supports(), w);
        }
        this.walkers = Map.copyOf(map);
    }

    /**
     * @return an immutable view of which walker types are wired —
     *         used by the health indicator + capabilities advert.
     */
    public Map<ArchiveType, ArchiveWalker> walkers() {
        return walkers;
    }

    /**
     * Detect + dispatch. Reads {@code input} in a single pass.
     *
     * @return the {@code DispatchResult} carrying the detected mime
     *         type + selected archive type. Children are emitted via
     *         {@code emitter} during the call.
     */
    @Observed(name = "archive.walk",
            contextualName = "archive-walk",
            lowCardinalityKeyValues = {"component", "archive"})
    public DispatchResult dispatch(InputStream input, String fileName, ChildEmitter emitter) {
        BufferedInputStream buffered = input instanceof BufferedInputStream b
                ? b : new BufferedInputStream(input, 16 * 1024);
        String mime;
        try {
            mime = tika.detect(buffered, fileName);
        } catch (IOException e) {
            throw new UncheckedIOException("archive mime detection failed", e);
        }
        ArchiveType type = pickType(mime);
        ArchiveWalker walker = walkers.get(type);
        if (walker == null) {
            throw new UnsupportedArchiveTypeException(mime);
        }
        walker.walk(buffered, fileName, emitter);
        return new DispatchResult(type, mime);
    }

    private static ArchiveType pickType(String mime) {
        if (mime == null) {
            throw new UnsupportedArchiveTypeException("null");
        }
        // Tika reports canonical mimes; aliases are normalised already.
        // application/zip + the JAR / docx / xlsx / pptx zip-derivatives:
        // we accept only the bare zip mime, since the sub-types should
        // be parsed by their domain-specific extractor (Tika service for
        // office docs, etc.) — not unpacked as raw zip.
        if ("application/zip".equals(mime)) {
            return ArchiveType.ZIP;
        }
        if ("application/mbox".equals(mime)) {
            return ArchiveType.MBOX;
        }
        if ("application/vnd.ms-outlook".equals(mime)
                || "application/vnd.ms-outlook-pst".equals(mime)) {
            return ArchiveType.PST;
        }
        throw new UnsupportedArchiveTypeException(mime);
    }

    /** Carries the dispatcher's findings back to the controller. */
    public record DispatchResult(ArchiveType type, String detectedMimeType) {
    }
}
