package co.uk.wolfnotsheep.extraction.archive.parse;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArchiveWalkerDispatcherTest {

    private final ArchiveWalkerDispatcher dispatcher = new ArchiveWalkerDispatcher(
            List.of(new ZipArchiveWalker(), new MboxArchiveWalker()));

    @Test
    void dispatches_zip_to_zip_walker() throws IOException {
        byte[] zip = makeZip();
        int[] childCount = {0};
        ChildEmitter emitter = (fileName, archivePath, hint, size, content) -> {
            childCount[0]++;
            drain(content);
        };

        ArchiveWalkerDispatcher.DispatchResult result =
                dispatcher.dispatch(new ByteArrayInputStream(zip), "test.zip", emitter);

        assertThat(result.type()).isEqualTo(ArchiveType.ZIP);
        assertThat(result.detectedMimeType()).isEqualTo("application/zip");
        assertThat(childCount[0]).isEqualTo(1);
    }

    @Test
    void dispatches_mbox_to_mbox_walker() {
        String mbox = "From me@a.test Mon Jan  1 00:00:00 2026\nSubject: hi\n\nbody\n";
        int[] childCount = {0};
        ArchiveWalkerDispatcher.DispatchResult result = dispatcher.dispatch(
                new ByteArrayInputStream(mbox.getBytes(StandardCharsets.UTF_8)),
                "test.mbox",
                (fileName, archivePath, hint, size, content) -> {
                    childCount[0]++;
                    drain(content);
                });

        assertThat(result.type()).isEqualTo(ArchiveType.MBOX);
        assertThat(result.detectedMimeType()).isEqualTo("application/mbox");
        assertThat(childCount[0]).isEqualTo(1);
    }

    @Test
    void rejects_unsupported_mime_type() {
        // Plain text — Tika detects text/plain, no walker handles it.
        byte[] bytes = "just a string, not an archive".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> dispatcher.dispatch(
                new ByteArrayInputStream(bytes), "x.txt",
                (fileName, archivePath, hint, size, content) -> drain(content)))
                .isInstanceOf(UnsupportedArchiveTypeException.class);
    }

    @Test
    void walkers_view_advertises_registered_types() {
        assertThat(dispatcher.walkers().keySet()).containsExactlyInAnyOrder(
                ArchiveType.ZIP, ArchiveType.MBOX);
    }

    private static byte[] makeZip() throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream out = new ZipOutputStream(bos)) {
            out.putNextEntry(new ZipEntry("ok.txt"));
            out.write("ok".getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        return bos.toByteArray();
    }

    private static void drain(InputStream in) {
        try {
            in.readAllBytes();
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }
}
