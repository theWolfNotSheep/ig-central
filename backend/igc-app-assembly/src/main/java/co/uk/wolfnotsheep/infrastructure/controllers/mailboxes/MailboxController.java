package co.uk.wolfnotsheep.infrastructure.controllers.mailboxes;

import co.uk.wolfnotsheep.document.models.ConnectedDrive;
import co.uk.wolfnotsheep.document.models.DocumentModel;
import co.uk.wolfnotsheep.document.repositories.ConnectedDriveRepository;
import co.uk.wolfnotsheep.infrastructure.services.drives.GoogleDriveService;
import co.uk.wolfnotsheep.infrastructure.services.mail.EmailIngestionService;
import co.uk.wolfnotsheep.infrastructure.services.mail.GmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

/**
 * Gmail OAuth, message browsing, and manual import endpoints.
 */
@RestController
@RequestMapping("/api/mailboxes")
public class MailboxController {

    private static final Logger log = LoggerFactory.getLogger(MailboxController.class);

    private final ConnectedDriveRepository driveRepo;
    private final GoogleDriveService googleDriveService;
    private final GmailService gmailService;
    private final EmailIngestionService emailIngestionService;
    private final co.uk.wolfnotsheep.infrastructure.services.PipelineThrottleService throttleService;

    public MailboxController(ConnectedDriveRepository driveRepo,
                              GoogleDriveService googleDriveService,
                              GmailService gmailService,
                              EmailIngestionService emailIngestionService,
                              co.uk.wolfnotsheep.infrastructure.services.PipelineThrottleService throttleService) {
        this.driveRepo = driveRepo;
        this.googleDriveService = googleDriveService;
        this.gmailService = gmailService;
        this.emailIngestionService = emailIngestionService;
        this.throttleService = throttleService;
    }

    // ── OAuth2 Flow ──────────────────────────────────────

    @GetMapping("/gmail/auth-url")
    public ResponseEntity<Map<String, String>> getAuthUrl(@AuthenticationPrincipal UserDetails user) {
        if (!googleDriveService.isConfigured()) {
            return ResponseEntity.badRequest().body(Map.of("error",
                    "Google OAuth not configured. Admin must set OAuth credentials in Settings."));
        }
        String state = Base64.getEncoder().encodeToString(
                ("GMAIL:" + user.getUsername()).getBytes());
        String url = gmailService.getGmailAuthorizationUrl(state);
        return ResponseEntity.ok(Map.of("url", url));
    }

    @GetMapping("/gmail/callback")
    public ResponseEntity<String> handleCallback(
            @RequestParam String code,
            @RequestParam String state) {
        try {
            String decoded = new String(Base64.getDecoder().decode(state));
            String email = decoded.startsWith("GMAIL:") ? decoded.substring(6) : decoded;

            var tokens = googleDriveService.exchangeCode(code);
            var userInfo = googleDriveService.getUserInfo(tokens.accessToken());

            Optional<ConnectedDrive> existing = driveRepo.findByUserIdAndProviderAndProviderAccountEmail(
                    email, "GMAIL", userInfo.get("email"));

            ConnectedDrive account;
            if (existing.isPresent()) {
                account = existing.get();
            } else {
                account = new ConnectedDrive();
                account.setUserId(email);
                account.setProvider("GMAIL");
                account.setProviderType(co.uk.wolfnotsheep.document.models.StorageProviderType.GMAIL);
                account.setDisplayName(userInfo.get("name") + " (Gmail)");
                account.setProviderAccountEmail(userInfo.get("email"));
                account.setProviderAccountName(userInfo.get("name"));
                account.setConnectedAt(Instant.now());
            }

            account.setAccessToken(tokens.accessToken());
            account.setRefreshToken(tokens.refreshToken() != null ? tokens.refreshToken() : account.getRefreshToken());
            account.setTokenExpiresAt(Instant.now().plusSeconds(tokens.expiresIn()));
            if (tokens.scope() != null && !tokens.scope().isBlank()) {
                account.setGrantedScopes(tokens.scope());
            }
            account.setActive(true);
            driveRepo.save(account);

            log.info("Gmail connected for user {} ({})", email, userInfo.get("email"));

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, "text/html")
                    .body("""
                        <html><body><script>
                        if (window.opener) {
                            window.opener.postMessage({ type: 'gmail-connected' }, '*');
                            window.close();
                        } else {
                            window.location.href = '/mailboxes?connected=true';
                        }
                        </script><p>Connected! You can close this window.</p></body></html>
                    """);

        } catch (Exception e) {
            log.error("Gmail OAuth callback failed: {}", e.getMessage(), e);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, "text/html")
                    .body("""
                        <html><body><script>
                        if (window.opener) {
                            window.opener.postMessage({ type: 'gmail-error', error: 'auth_failed' }, '*');
                            window.close();
                        } else {
                            window.location.href = '/mailboxes?error=auth_failed';
                        }
                        </script><p>Connection failed. You can close this window.</p></body></html>
                    """);
        }
    }

    // ── Connected Mailboxes ──────────────────────────────

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listMailboxes(@AuthenticationPrincipal UserDetails user) {
        List<ConnectedDrive> accounts = driveRepo.findAccessibleByProvider(user.getUsername(), "GMAIL");
        List<Map<String, Object>> result = new ArrayList<>();
        for (ConnectedDrive a : accounts) {
            result.add(Map.of(
                    "id", a.getId(),
                    "displayName", a.getDisplayName() != null ? a.getDisplayName() : "",
                    "providerAccountEmail", a.getProviderAccountEmail() != null ? a.getProviderAccountEmail() : "",
                    "providerAccountName", a.getProviderAccountName() != null ? a.getProviderAccountName() : "",
                    "active", a.isActive(),
                    "connectedAt", a.getConnectedAt() != null ? a.getConnectedAt().toString() : ""
            ));
        }
        return ResponseEntity.ok(result);
    }

    // ── Browse Messages ──────────────────────────────────

    @GetMapping("/{accountId}/messages")
    public ResponseEntity<?> listMessages(
            @PathVariable String accountId,
            @RequestParam(defaultValue = "") String q,
            @RequestParam(required = false) String pageToken,
            @RequestParam(defaultValue = "20") int maxResults) {
        ConnectedDrive account = driveRepo.findById(accountId).orElse(null);
        if (account == null || !account.isActive() || !"GMAIL".equals(account.getProvider())) {
            return ResponseEntity.notFound().build();
        }

        try {
            var result = gmailService.listMessages(account, q, pageToken, maxResults);
            return ResponseEntity.ok(Map.of(
                    "messages", result.messages(),
                    "nextPageToken", result.nextPageToken() != null ? result.nextPageToken() : "",
                    "resultSizeEstimate", result.resultSizeEstimate()
            ));
        } catch (Exception e) {
            log.error("Failed to list messages for account {}: {}", accountId, e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to list messages: " + e.getMessage()));
        }
    }

    @GetMapping("/{accountId}/messages/{messageId}")
    public ResponseEntity<?> getMessageDetail(
            @PathVariable String accountId,
            @PathVariable String messageId) {
        ConnectedDrive account = driveRepo.findById(accountId).orElse(null);
        if (account == null || !account.isActive() || !"GMAIL".equals(account.getProvider())) {
            return ResponseEntity.notFound().build();
        }

        try {
            var message = gmailService.getMessage(account, messageId);
            String body = gmailService.extractBody(message);
            var attachments = gmailService.extractAttachments(message);

            return ResponseEntity.ok(Map.of(
                    "id", message.getId(),
                    "threadId", message.getThreadId(),
                    "snippet", message.getSnippet() != null ? message.getSnippet() : "",
                    "body", body,
                    "attachments", attachments
            ));
        } catch (Exception e) {
            log.error("Failed to get message {}: {}", messageId, e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to get message: " + e.getMessage()));
        }
    }

    // ── Import Messages ──────────────────────────────────

    @PostMapping("/{accountId}/messages/import")
    public ResponseEntity<?> importMessages(
            @PathVariable String accountId,
            @RequestBody ImportRequest request,
            @AuthenticationPrincipal UserDetails user) {
        ConnectedDrive account = driveRepo.findById(accountId).orElse(null);
        if (account == null || !account.isActive() || !"GMAIL".equals(account.getProvider())) {
            return ResponseEntity.notFound().build();
        }

        // Throttle check
        String throttleError = throttleService.checkThrottle(request.messageIds().size());
        if (throttleError != null) {
            return ResponseEntity.status(429).body(Map.of("error", throttleError));
        }

        int imported = 0;
        int skipped = 0;
        int failed = 0;

        for (String messageId : request.messageIds()) {
            try {
                DocumentModel doc = emailIngestionService.ingestMessage(
                        account, messageId, user.getUsername(), null);
                if (doc != null) {
                    imported++;
                } else {
                    skipped++;
                }
            } catch (Exception e) {
                log.error("Failed to import message {}: {}", messageId, e.getMessage());
                failed++;
            }
        }

        return ResponseEntity.ok(Map.of(
                "imported", imported,
                "skipped", skipped,
                "failed", failed
        ));
    }

    // ── Disconnect ───────────────────────────────────────

    @DeleteMapping("/{accountId}")
    public ResponseEntity<?> disconnect(@PathVariable String accountId) {
        ConnectedDrive account = driveRepo.findById(accountId).orElse(null);
        if (account == null || !"GMAIL".equals(account.getProvider())) {
            return ResponseEntity.notFound().build();
        }

        account.setActive(false);
        driveRepo.save(account);
        log.info("Gmail account disconnected: {}", account.getProviderAccountEmail());
        return ResponseEntity.ok(Map.of("message", "Gmail account disconnected"));
    }

    record ImportRequest(List<String> messageIds) {}
}
