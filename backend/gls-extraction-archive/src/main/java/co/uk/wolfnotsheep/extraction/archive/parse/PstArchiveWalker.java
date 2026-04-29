package co.uk.wolfnotsheep.extraction.archive.parse;

import com.pff.PSTException;
import com.pff.PSTFile;
import com.pff.PSTFolder;
import com.pff.PSTMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Vector;

/**
 * Walks an Outlook PST archive and emits one synthesised RFC 822
 * {@code .eml} per message — headers + body, no attachments. Each
 * emitted child re-routes through the pipeline as a standalone email,
 * matching the {@code MboxArchiveWalker}'s output shape so downstream
 * extractors don't need to special-case PST.
 *
 * <p>java-libpst (CSV #44) requires a {@code RandomAccessFile}, so the
 * walker materialises the source stream to a temp file before opening.
 * The temp file is deleted on completion (success or failure).
 *
 * <p>**Limitations** documented for the next walker pass:
 *
 * <ul>
 *     <li>Attachments are not extracted — they're dropped on the floor.
 *         The follow-up adds attachment children with `archivePath` set
 *         to the parent message id.</li>
 *     <li>Only the plain-text body is emitted; HTML body and rich-text
 *         body are ignored.</li>
 *     <li>Encrypted PSTs that require a password fail with
 *         {@link CorruptArchiveException}.</li>
 * </ul>
 */
@Component
public class PstArchiveWalker implements ArchiveWalker {

    private static final Logger log = LoggerFactory.getLogger(PstArchiveWalker.class);
    private static final String EML_MIME = "message/rfc822";
    private static final DateTimeFormatter RFC_822 =
            DateTimeFormatter.ofPattern("EEE, d MMM yyyy HH:mm:ss Z");

    @Override
    public ArchiveType supports() {
        return ArchiveType.PST;
    }

    @Override
    public void walk(InputStream input, String fileName, ChildEmitter emitter) {
        Path temp;
        try {
            temp = Files.createTempFile("gls-archive-pst-", ".pst");
            Files.copy(input, temp, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("PST temp materialisation failed", e);
        }
        try {
            PSTFile pst = new PSTFile(temp.toFile());
            int[] index = {0};
            walkFolder(pst.getRootFolder(), emitter, index);
        } catch (PSTException e) {
            throw new CorruptArchiveException("PST appears invalid or password-protected: " + e.getMessage(), e);
        } catch (IOException e) {
            throw new CorruptArchiveException("PST read failed: " + e.getMessage(), e);
        } finally {
            try {
                Files.deleteIfExists(temp);
            } catch (IOException e) {
                log.warn("PST temp file cleanup failed: {} ({})", temp, e.getMessage());
            }
        }
    }

    private void walkFolder(PSTFolder folder, ChildEmitter emitter, int[] index) {
        try {
            // Recurse into sub-folders. java-libpst's `getSubFolders()`
            // returns the immediate children only — depth is unbounded
            // here within the PST's own folder hierarchy, but each
            // emitted *message* still becomes a single direct child of
            // the archive request itself, matching CSV #43's
            // one-level-per-invocation rule.
            Vector<PSTFolder> subs = folder.getSubFolders();
            for (PSTFolder sub : subs) {
                walkFolder(sub, emitter, index);
            }
            if (folder.getContentCount() > 0) {
                PSTMessage msg;
                while ((msg = (PSTMessage) folder.getNextChild()) != null) {
                    emitMessage(msg, folder.getDisplayName(), emitter, index[0]++);
                }
            }
        } catch (PSTException | IOException e) {
            throw new CorruptArchiveException("PST folder walk failed: " + e.getMessage(), e);
        }
    }

    private void emitMessage(PSTMessage msg, String folderPath, ChildEmitter emitter, int index) {
        StringBuilder eml = new StringBuilder(2048);
        appendHeader(eml, "From", asEmail(msg.getSenderName(), msg.getSenderEmailAddress()));
        appendHeader(eml, "To", nullSafe(msg.getDisplayTo()));
        if (notBlank(msg.getDisplayCC())) appendHeader(eml, "Cc", msg.getDisplayCC());
        appendHeader(eml, "Subject", nullSafe(msg.getSubject()));
        Date sent = msg.getMessageDeliveryTime();
        if (sent == null) sent = msg.getClientSubmitTime();
        if (sent != null) {
            appendHeader(eml, "Date",
                    OffsetDateTime.ofInstant(sent.toInstant(), ZoneOffset.UTC).format(RFC_822));
        }
        if (notBlank(msg.getInternetMessageId())) {
            appendHeader(eml, "Message-ID", msg.getInternetMessageId());
        }
        eml.append("MIME-Version: 1.0\r\n");
        eml.append("Content-Type: text/plain; charset=utf-8\r\n");
        eml.append("\r\n");
        String body = nullSafe(msg.getBody());
        eml.append(body);

        byte[] bytes = eml.toString().getBytes(StandardCharsets.UTF_8);
        String fileName = "message-" + index + ".eml";
        emitter.onChild(fileName, folderPath, EML_MIME, bytes.length,
                new ByteArrayInputStream(bytes));
    }

    private static void appendHeader(StringBuilder sb, String name, String value) {
        sb.append(name).append(": ").append(value.replace("\r", "").replace("\n", " "))
                .append("\r\n");
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String asEmail(String name, String address) {
        if (notBlank(name) && notBlank(address)) {
            return name + " <" + address + ">";
        }
        if (notBlank(address)) return address;
        if (notBlank(name)) return name;
        return "unknown";
    }
}
