package co.uk.wolfnotsheep.extraction.archive.parse;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipException;

/**
 * Streams a ZIP archive using Apache Commons Compress and emits each
 * top-level file entry as a child. Directory entries are skipped (they
 * carry no bytes and exist only to define hierarchy). Encrypted
 * entries are reported as {@link CorruptArchiveException} — passwords
 * are not in scope for this walker.
 *
 * <p>One-level walk — entries inside nested archives are not unpacked
 * recursively. A ZIP-in-ZIP child is emitted as a fresh
 * {@code documentRef} and the orchestrator routes it back through
 * this service on its own request (CSV #43).
 */
@Component
public class ZipArchiveWalker implements ArchiveWalker {

    @Override
    public ArchiveType supports() {
        return ArchiveType.ZIP;
    }

    @Override
    public void walk(InputStream input, String fileName, ChildEmitter emitter) {
        // Commons Compress's ZipArchiveInputStream supports streaming
        // central-directory-less reads — works against a single pass
        // of the source bytes. The trade-off is sizes are sometimes
        // unknown until the deflate stream hits its trailer; we pass
        // -1 to the emitter when that's the case.
        try (ZipArchiveInputStream zip = new ZipArchiveInputStream(input)) {
            ZipArchiveEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                if (!zip.canReadEntryData(entry)) {
                    throw new CorruptArchiveException(
                            "ZIP entry not readable (likely encrypted): " + entry.getName());
                }
                long size = entry.getSize();
                emitter.onChild(
                        leafName(entry.getName()),
                        entry.getName(),
                        /* contentTypeHint */ null,
                        size,
                        new EntryStream(zip));
            }
        } catch (ZipException e) {
            throw new CorruptArchiveException("ZIP appears truncated or invalid: " + e.getMessage(), e);
        } catch (IOException e) {
            // The source.open() boundary already passed — any IO error
            // from Commons Compress's parser at this point indicates
            // bad bytes (truncated central directory, EOF mid-entry,
            // etc.), not a source-side I/O failure. Treat as corruption
            // so the caller gets a 422 with code ARCHIVE_CORRUPT.
            throw new CorruptArchiveException("ZIP read failed mid-stream: " + e.getMessage(), e);
        }
    }

    private static String leafName(String entryName) {
        if (entryName == null || entryName.isBlank()) {
            return "child";
        }
        int slash = Math.max(entryName.lastIndexOf('/'), entryName.lastIndexOf('\\'));
        return slash < 0 || slash == entryName.length() - 1
                ? entryName
                : entryName.substring(slash + 1);
    }

    /**
     * View of the underlying {@link ZipArchiveInputStream} that the
     * emitter reads to get the current entry's bytes. Closing this
     * stream is a no-op: closing the outer ZIP stream is the walker's
     * responsibility, and Commons Compress advances to the next entry
     * lazily on the next {@code getNextEntry()} regardless of whether
     * the previous entry's bytes were fully drained.
     */
    private static final class EntryStream extends InputStream {
        private final ZipArchiveInputStream zip;

        EntryStream(ZipArchiveInputStream zip) {
            this.zip = zip;
        }

        @Override
        public int read() throws IOException {
            return zip.read();
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            return zip.read(b, off, len);
        }

        @Override
        public void close() {
            // Don't close the zip — we still have entries to read.
        }
    }
}
