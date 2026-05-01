package co.uk.wolfnotsheep.infrastructure.services.mail;

import co.uk.wolfnotsheep.document.models.ConnectedDrive;
import co.uk.wolfnotsheep.infrastructure.services.drives.GoogleDriveService;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.*;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.GeneralSecurityException;
import java.util.*;

/**
 * Gmail API wrapper. Lists messages, fetches full content,
 * extracts body text and attachments.
 */
@Service
public class GmailService {

    private static final Logger log = LoggerFactory.getLogger(GmailService.class);
    private static final GsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();

    private final GoogleDriveService googleDriveService;

    public GmailService(GoogleDriveService googleDriveService) {
        this.googleDriveService = googleDriveService;
    }

    // ── Records ──────────────────────────────────────────────

    public record GmailMessageSummary(String id, String threadId, String snippet,
                                       long internalDate, String from, String subject,
                                       boolean hasAttachments) {}

    public record EmailAttachment(String filename, String mimeType, long sizeBytes,
                                   String attachmentId, String partId) {}

    // ── List messages ────────────────────────────────────────

    public record MessageListResult(List<GmailMessageSummary> messages, String nextPageToken,
                                     long resultSizeEstimate) {}

    public MessageListResult listMessages(ConnectedDrive account, String query,
                                           String pageToken, int maxResults) throws Exception {
        Gmail gmail = buildGmailService(account);

        Gmail.Users.Messages.List request = gmail.users().messages().list("me")
                .setMaxResults((long) maxResults);
        if (query != null && !query.isBlank()) request.setQ(query);
        if (pageToken != null && !pageToken.isBlank()) request.setPageToken(pageToken);

        ListMessagesResponse response = request.execute();

        if (response.getMessages() == null) {
            return new MessageListResult(List.of(), null, 0);
        }

        // Response only has id + threadId — fetch minimal headers for each
        List<GmailMessageSummary> summaries = new ArrayList<>();
        for (com.google.api.services.gmail.model.Message msg : response.getMessages()) {
            try {
                com.google.api.services.gmail.model.Message full = gmail.users().messages()
                        .get("me", msg.getId())
                        .setFormat("metadata")
                        .setMetadataHeaders(List.of("From", "Subject", "Content-Type"))
                        .execute();

                String from = getHeader(full, "From");
                String subject = getHeader(full, "Subject");
                String contentType = getHeader(full, "Content-Type");
                boolean hasAttach = contentType.contains("mixed") || hasAttachments(full);

                summaries.add(new GmailMessageSummary(
                        full.getId(), full.getThreadId(),
                        full.getSnippet(), full.getInternalDate(),
                        from, subject, hasAttach));
            } catch (Exception e) {
                log.warn("Failed to fetch summary for message {}: {}", msg.getId(), e.getMessage());
            }
        }

        return new MessageListResult(summaries, response.getNextPageToken(),
                response.getResultSizeEstimate());
    }

    // ── Get full message ─────────────────────────────────────

    public com.google.api.services.gmail.model.Message getMessage(ConnectedDrive account,
                                                                    String messageId) throws Exception {
        Gmail gmail = buildGmailService(account);
        return gmail.users().messages().get("me", messageId)
                .setFormat("full")
                .execute();
    }

    // ── Extract body text ────────────────────────────────────

    public String extractBody(com.google.api.services.gmail.model.Message message) {
        if (message.getPayload() == null) return "";

        // Try to find text/plain first, fall back to text/html
        String plainText = findBodyByMimeType(message.getPayload(), "text/plain");
        if (plainText != null && !plainText.isBlank()) return plainText;

        String htmlText = findBodyByMimeType(message.getPayload(), "text/html");
        if (htmlText != null && !htmlText.isBlank()) return stripHtml(htmlText);

        return "";
    }

    private String findBodyByMimeType(MessagePart part, String mimeType) {
        if (part.getMimeType() != null && part.getMimeType().equalsIgnoreCase(mimeType)) {
            if (part.getBody() != null && part.getBody().getData() != null) {
                return new String(Base64.getUrlDecoder().decode(part.getBody().getData()));
            }
        }
        if (part.getParts() != null) {
            for (MessagePart child : part.getParts()) {
                String result = findBodyByMimeType(child, mimeType);
                if (result != null) return result;
            }
        }
        return null;
    }

    // ── Extract attachments ──────────────────────────────────

    public List<EmailAttachment> extractAttachments(com.google.api.services.gmail.model.Message message) {
        if (message.getPayload() == null) return List.of();
        List<EmailAttachment> attachments = new ArrayList<>();
        collectAttachments(message.getPayload(), attachments);
        return attachments;
    }

    private void collectAttachments(MessagePart part, List<EmailAttachment> attachments) {
        if (part.getFilename() != null && !part.getFilename().isBlank()
                && part.getBody() != null && part.getBody().getAttachmentId() != null) {
            attachments.add(new EmailAttachment(
                    part.getFilename(),
                    part.getMimeType(),
                    part.getBody().getSize() != null ? part.getBody().getSize() : 0,
                    part.getBody().getAttachmentId(),
                    part.getPartId()));
        }
        if (part.getParts() != null) {
            for (MessagePart child : part.getParts()) {
                collectAttachments(child, attachments);
            }
        }
    }

    // ── Download attachment ──────────────────────────────────

    public byte[] downloadAttachment(ConnectedDrive account, String messageId,
                                      String attachmentId) throws Exception {
        Gmail gmail = buildGmailService(account);
        MessagePartBody body = gmail.users().messages().attachments()
                .get("me", messageId, attachmentId)
                .execute();
        return Base64.getUrlDecoder().decode(body.getData());
    }

    // ── OAuth URL for Gmail ──────────────────────────────────

    public String getGmailAuthorizationUrl(String state) {
        return "https://accounts.google.com/o/oauth2/v2/auth" +
                "?client_id=" + googleDriveService.getClientId() +
                "&redirect_uri=" + googleDriveService.getRedirectUri() +
                "&response_type=code" +
                "&scope=https://www.googleapis.com/auth/gmail.readonly%20" +
                "https://www.googleapis.com/auth/userinfo.email%20" +
                "https://www.googleapis.com/auth/userinfo.profile" +
                "&access_type=offline" +
                "&prompt=consent" +
                "&state=" + state;
    }

    // ── Helpers ──────────────────────────────────────────────

    private Gmail buildGmailService(ConnectedDrive account) throws GeneralSecurityException, Exception {
        ConnectedDrive refreshed = googleDriveService.refreshTokenIfNeeded(account);
        HttpTransport transport = GoogleNetHttpTransport.newTrustedTransport();

        GoogleCredentials credentials;
        if (refreshed.getRefreshToken() != null && !refreshed.getRefreshToken().isBlank()) {
            credentials = com.google.auth.oauth2.UserCredentials.newBuilder()
                    .setClientId(googleDriveService.getClientId())
                    .setClientSecret(googleDriveService.getClientSecret())
                    .setRefreshToken(refreshed.getRefreshToken())
                    .setAccessToken(new AccessToken(refreshed.getAccessToken(),
                            Date.from(refreshed.getTokenExpiresAt())))
                    .build();
        } else {
            credentials = GoogleCredentials.create(
                    new AccessToken(refreshed.getAccessToken(), Date.from(refreshed.getTokenExpiresAt())));
        }

        return new Gmail.Builder(transport, JSON_FACTORY, new HttpCredentialsAdapter(credentials))
                .setApplicationName("IG Central")
                .build();
    }

    private String getHeader(com.google.api.services.gmail.model.Message message, String name) {
        if (message.getPayload() != null && message.getPayload().getHeaders() != null) {
            for (MessagePartHeader header : message.getPayload().getHeaders()) {
                if (name.equalsIgnoreCase(header.getName())) return header.getValue();
            }
        }
        return "";
    }

    private boolean hasAttachments(com.google.api.services.gmail.model.Message message) {
        if (message.getPayload() == null || message.getPayload().getParts() == null) return false;
        return message.getPayload().getParts().stream()
                .anyMatch(p -> p.getFilename() != null && !p.getFilename().isBlank());
    }

    private String stripHtml(String html) {
        return html.replaceAll("<[^>]+>", "")
                .replaceAll("&nbsp;", " ")
                .replaceAll("&amp;", "&")
                .replaceAll("&lt;", "<")
                .replaceAll("&gt;", ">")
                .replaceAll("&#\\d+;", "")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
