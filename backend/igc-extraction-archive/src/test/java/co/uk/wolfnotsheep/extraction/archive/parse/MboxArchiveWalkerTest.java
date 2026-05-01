package co.uk.wolfnotsheep.extraction.archive.parse;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MboxArchiveWalkerTest {

    private final MboxArchiveWalker walker = new MboxArchiveWalker();

    @Test
    void walk_splits_two_messages() {
        String mbox = """
                From sender@a.test Mon Jan  1 00:00:00 2026
                Subject: Hi

                hello world

                From other@b.test Tue Jan  2 00:00:00 2026
                Subject: Re: Hi

                a reply
                """;

        List<Captured> captured = collect(mbox);

        assertThat(captured).hasSize(2);
        assertThat(captured.get(0).fileName).isEqualTo("message-0.eml");
        assertThat(captured.get(0).hint).isEqualTo("message/rfc822");
        assertThat(captured.get(0).bytes).contains("Subject: Hi");
        assertThat(captured.get(0).bytes).contains("hello world");
        assertThat(captured.get(1).fileName).isEqualTo("message-1.eml");
        assertThat(captured.get(1).bytes).contains("Subject: Re: Hi");
    }

    @Test
    void walk_emits_zero_for_empty_input() {
        List<Captured> captured = collect("");
        assertThat(captured).isEmpty();
    }

    @Test
    void walk_emits_single_message_when_only_one_separator() {
        String mbox = """
                From only@a.test Mon Jan  1 00:00:00 2026
                Subject: Solo

                content here
                """;

        List<Captured> captured = collect(mbox);
        assertThat(captured).hasSize(1);
        assertThat(captured.get(0).bytes).contains("Solo");
    }

    @Test
    void walk_includes_separator_line_in_emitted_message() {
        String mbox = "From sender@a.test Mon Jan  1 00:00:00 2026\nSubject: x\n\nbody\n";
        List<Captured> captured = collect(mbox);
        assertThat(captured).hasSize(1);
        // The "From " envelope line itself is preserved on the emitted .eml
        // so a downstream MIME parser sees a complete RFC 822 context.
        assertThat(captured.get(0).bytes).startsWith("From sender@a.test");
    }

    @Test
    void walk_ignores_garbage_before_first_separator() {
        String mbox = """
                garbage text that doesn't belong
                another stray line
                From real@a.test Mon Jan  1 00:00:00 2026
                Subject: First real message

                body
                """;

        List<Captured> captured = collect(mbox);
        assertThat(captured).hasSize(1);
        assertThat(captured.get(0).bytes).contains("First real message");
        assertThat(captured.get(0).bytes).doesNotContain("garbage");
    }

    private List<Captured> collect(String mbox) {
        List<Captured> captured = new ArrayList<>();
        walker.walk(new ByteArrayInputStream(mbox.getBytes(StandardCharsets.UTF_8)),
                "test.mbox",
                (fileName, archivePath, hint, size, content) ->
                        captured.add(new Captured(fileName, hint, drain(content))));
        return captured;
    }

    private static String drain(InputStream in) {
        try {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    private record Captured(String fileName, String hint, String bytes) {
    }
}
