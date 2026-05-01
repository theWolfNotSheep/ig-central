package co.uk.wolfnotsheep.extraction.archive.parse;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ZipArchiveWalkerTest {

    private final ZipArchiveWalker walker = new ZipArchiveWalker();

    @Test
    void walk_emits_one_child_per_top_level_file_entry() throws IOException {
        byte[] zip = buildZip(
                entry("hello.txt", "hello"),
                entry("nested/dir/inner.txt", "deep"),
                entry("blank.bin", ""));

        List<Captured> captured = new ArrayList<>();
        ChildEmitter emitter = (fileName, archivePath, hint, size, content) ->
                captured.add(new Captured(fileName, archivePath, size, drain(content)));

        walker.walk(new ByteArrayInputStream(zip), "test.zip", emitter);

        assertThat(captured).hasSize(3);
        assertThat(captured.get(0).fileName).isEqualTo("hello.txt");
        assertThat(captured.get(0).archivePath).isEqualTo("hello.txt");
        assertThat(captured.get(0).bytes).isEqualTo("hello");
        assertThat(captured.get(1).fileName).isEqualTo("inner.txt");
        assertThat(captured.get(1).archivePath).isEqualTo("nested/dir/inner.txt");
        assertThat(captured.get(2).fileName).isEqualTo("blank.bin");
        assertThat(captured.get(2).bytes).isEqualTo("");
    }

    @Test
    void walk_skips_directory_entries() throws IOException {
        // ZipOutputStream.putNextEntry with a name ending "/" creates a
        // directory entry — walker should not emit a child for it.
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream out = new ZipOutputStream(bos)) {
            out.putNextEntry(new ZipEntry("emptydir/"));
            out.closeEntry();
            out.putNextEntry(new ZipEntry("emptydir/file.txt"));
            out.write("ok".getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }

        List<Captured> captured = new ArrayList<>();
        walker.walk(new ByteArrayInputStream(bos.toByteArray()), "x.zip",
                (fn, ap, hint, size, content) -> captured.add(new Captured(fn, ap, size, drain(content))));

        assertThat(captured).hasSize(1);
        assertThat(captured.get(0).fileName).isEqualTo("file.txt");
    }

    @Test
    void walk_propagates_emitter_caps_exceptions_unchanged() throws IOException {
        byte[] zip = buildZip(entry("a", "1"), entry("b", "2"));
        ChildEmitter capper = (fileName, archivePath, hint, size, content) -> {
            throw new ArchiveCapsExceededException(
                    ArchiveCapsExceededException.Cap.ARCHIVE_TOO_MANY_CHILDREN,
                    "fixture cap");
        };

        assertThatThrownBy(() -> walker.walk(new ByteArrayInputStream(zip), "x.zip", capper))
                .isInstanceOf(ArchiveCapsExceededException.class);
    }

    @Test
    void walk_throws_corrupt_on_truncated_zip() {
        // Take a valid zip and lop the trailing central directory off so
        // the stream looks broken to the parser.
        try {
            byte[] zip = buildZip(entry("ok.txt", "value"));
            byte[] truncated = new byte[zip.length / 2];
            System.arraycopy(zip, 0, truncated, 0, truncated.length);

            assertThatThrownBy(() -> walker.walk(new ByteArrayInputStream(truncated), "x.zip",
                    (fn, ap, hint, size, content) -> drain(content)))
                    .isInstanceOf(CorruptArchiveException.class);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    private static Entry entry(String name, String body) {
        return new Entry(name, body.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] buildZip(Entry... entries) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream out = new ZipOutputStream(bos)) {
            for (Entry e : entries) {
                out.putNextEntry(new ZipEntry(e.name));
                out.write(e.body);
                out.closeEntry();
            }
        }
        return bos.toByteArray();
    }

    private static String drain(java.io.InputStream in) {
        try {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    private record Entry(String name, byte[] body) {
    }

    private record Captured(String fileName, String archivePath, long size, String bytes) {
    }
}
