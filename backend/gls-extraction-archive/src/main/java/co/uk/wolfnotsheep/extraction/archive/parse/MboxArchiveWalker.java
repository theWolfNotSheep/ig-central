package co.uk.wolfnotsheep.extraction.archive.parse;

import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Splits an mbox file (RFC 4155 / mboxrd / mboxo variants) into one
 * RFC 822 message per child. Each message is emitted as a fresh
 * {@code .eml} entry so downstream extractors can process it as a
 * standalone email.
 *
 * <p>Attachments inside emails are NOT exploded here — that's
 * recursion the orchestrator handles by re-routing the {@code .eml}
 * back through the appropriate extractor on its own request, matching
 * the one-level-per-invocation rule from CSV #43.
 *
 * <p>Variant compatibility: this walker handles mboxrd (the most
 * common variant — quotes inner {@code "From "} lines as
 * {@code ">From "}) and mboxo (no quoting) by treating any line that
 * starts with {@code "From "} as a message separator unless it
 * appears inside the headers of the current message. False positives
 * on body lines like {@code "From the desk of …"} are rare in real
 * mailboxes; if they occur in practice we move to a stricter
 * detector.
 *
 * <p>Buffering: each message is materialised into memory before being
 * emitted because the contract gives the emitter (which uploads to
 * MinIO) the freedom to require a content-length up-front. Typical
 * mailbox messages are KB to a few MB; archives with multi-GB single
 * messages are an outlier and would hit the per-child cap before
 * memory is a concern.
 */
@Component
public class MboxArchiveWalker implements ArchiveWalker {

    private static final String SEPARATOR = "From ";
    private static final String EML_MIME = "message/rfc822";

    @Override
    public ArchiveType supports() {
        return ArchiveType.MBOX;
    }

    @Override
    public void walk(InputStream input, String fileName, ChildEmitter emitter) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8))) {

            StringBuilder current = null;
            int index = 0;
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith(SEPARATOR)) {
                    if (current != null && !current.isEmpty()) {
                        emit(emitter, index++, current);
                    }
                    current = new StringBuilder();
                    // The separator line itself is part of the message
                    // envelope (carries the envelope sender + delivery
                    // date). Keep it on the emitted .eml so MIME
                    // parsers see a complete RFC 822 message context.
                    current.append(line).append('\n');
                } else if (current != null) {
                    // mboxrd unquotes lines like ">From " back to "From "
                    // when reading; we keep the raw bytes and let
                    // downstream tools choose how to interpret them.
                    current.append(line).append('\n');
                }
                // Lines before the first "From " are skipped — they
                // shouldn't exist in a well-formed mbox, but tolerate
                // garbage at the head of file rather than failing the
                // whole walk.
            }

            if (current != null && !current.isEmpty()) {
                emit(emitter, index, current);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("mbox read failure", e);
        }
    }

    private static void emit(ChildEmitter emitter, int index, StringBuilder buffer) {
        byte[] bytes = buffer.toString().getBytes(StandardCharsets.UTF_8);
        emitter.onChild(
                "message-" + index + ".eml",
                /* archivePath */ null,
                EML_MIME,
                bytes.length,
                new ByteArrayInputStream(bytes));
    }
}
