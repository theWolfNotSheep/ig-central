package co.uk.wolfnotsheep.extraction.archive.parse;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Synthesising a valid PST in-process is impractical (java-libpst is
 * read-only; no library writes them under our licence). The walker's
 * happy path is covered by integration tests once issue #7 unblocks
 * Testcontainers + a checked-in fixture PST. These unit tests cover
 * the error-path semantics + the walker's static contract.
 */
class PstArchiveWalkerTest {

    private final PstArchiveWalker walker = new PstArchiveWalker();

    @Test
    void supports_returns_PST() {
        assertThat(walker.supports()).isEqualTo(ArchiveType.PST);
    }

    @Test
    void walk_with_garbage_bytes_throws_corrupt_archive() {
        byte[] junk = "this is definitely not a PST file".getBytes(StandardCharsets.UTF_8);
        List<String> emitted = new ArrayList<>();
        assertThatThrownBy(() -> walker.walk(new ByteArrayInputStream(junk), "fake.pst",
                (fileName, ap, hint, size, content) -> emitted.add(fileName)))
                .isInstanceOf(CorruptArchiveException.class);
        assertThat(emitted).isEmpty();
    }

    @Test
    void walk_with_empty_stream_throws_corrupt_archive() {
        InputStream empty = new ByteArrayInputStream(new byte[0]);
        assertThatThrownBy(() -> walker.walk(empty, "empty.pst",
                (fileName, ap, hint, size, content) -> {}))
                .isInstanceOf(CorruptArchiveException.class);
    }
}
