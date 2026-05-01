package co.uk.wolfnotsheep.infrastructure.controllers.auth;

import co.uk.wolfnotsheep.infrastructure.services.drives.GoogleDriveService;
import co.uk.wolfnotsheep.platform.config.services.AppConfigService;
import co.uk.wolfnotsheep.platform.identity.models.SignUpMethod;
import co.uk.wolfnotsheep.platform.identity.models.UserAccountType;
import co.uk.wolfnotsheep.platform.identity.models.UserFactory;
import co.uk.wolfnotsheep.platform.identity.models.UserModel;
import co.uk.wolfnotsheep.platform.identity.repositories.MongoUserRepository;
import co.uk.wolfnotsheep.platform.identity.utils.JwtUtilsWithCookieSupport;
import co.uk.wolfnotsheep.platform.products.services.RolePermissionSyncService;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

/**
 * Handles "Login with Google" OAuth2 flow.
 * Uses the same Google OAuth credentials configured in Settings.
 * Auto-provisions users on first login with default roles.
 */
@RestController
@RequestMapping("/api/auth/public/google")
public class GoogleAuthController {

    private static final Logger log = LoggerFactory.getLogger(GoogleAuthController.class);

    private final GoogleDriveService googleDriveService;
    private final MongoUserRepository userRepo;
    private final UserFactory userFactory;
    private final RolePermissionSyncService syncService;
    private final AppConfigService configService;
    private final JwtUtilsWithCookieSupport jwtUtils;

    @Value("${spring.application.jwtExpirationMs:172800000}")
    private long jwtExpirationMs;

    @Value("${public.url:http://localhost}")
    private String publicUrl;

    public GoogleAuthController(GoogleDriveService googleDriveService,
                                MongoUserRepository userRepo,
                                UserFactory userFactory,
                                RolePermissionSyncService syncService,
                                AppConfigService configService,
                                JwtUtilsWithCookieSupport jwtUtils) {
        this.googleDriveService = googleDriveService;
        this.jwtUtils = jwtUtils;
        this.userRepo = userRepo;
        this.userFactory = userFactory;
        this.syncService = syncService;
        this.configService = configService;
    }

    @GetMapping("/login")
    public ResponseEntity<Void> initiateLogin() {
        if (!googleDriveService.isConfigured()) {
            return ResponseEntity.badRequest().build();
        }

        String redirectUri = publicUrl + "/api/auth/public/google/callback";
        String clientId = configService.getValue("google.oauth.client_id", "");

        String url = "https://accounts.google.com/o/oauth2/v2/auth" +
                "?client_id=" + clientId +
                "&redirect_uri=" + redirectUri +
                "&response_type=code" +
                "&scope=openid%20email%20profile" +
                "&prompt=select_account";

        return ResponseEntity.status(302)
                .header(HttpHeaders.LOCATION, url)
                .build();
    }

    @GetMapping("/callback")
    public ResponseEntity<String> handleCallback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String error,
            @RequestParam(required = false) String scope,
            jakarta.servlet.http.HttpServletRequest request,
            HttpServletResponse response) {
        log.info("Google OAuth callback received. code={}, error={}, scope={}, queryString={}",
                code != null ? "present(" + code.length() + " chars)" : "null",
                error, scope, request.getQueryString());

        if (error != null) {
            log.error("Google OAuth error: {}", error);
            return redirect("/login?error=google_denied");
        }
        if (code == null || code.isBlank()) {
            log.error("Google OAuth callback missing code parameter. Full URL: {}?{}",
                    request.getRequestURL(), request.getQueryString());
            return redirect("/login?error=google_auth_failed");
        }
        try {
            // Exchange code — redirect URI must match what was sent in the initial request
            String clientId = configService.getValue("google.oauth.client_id", "");
            String clientSecret = configService.getValue("google.oauth.client_secret", "");
            String redirectUri = publicUrl + "/api/auth/public/google/callback";

            // Exchange code for tokens
            String tokenBody = "code=" + code +
                    "&client_id=" + clientId +
                    "&client_secret=" + clientSecret +
                    "&redirect_uri=" + redirectUri +
                    "&grant_type=authorization_code";

            java.net.http.HttpRequest tokenReq = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create("https://oauth2.googleapis.com/token"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(tokenBody))
                    .build();

            java.net.http.HttpResponse<String> tokenResp = java.net.http.HttpClient.newHttpClient()
                    .send(tokenReq, java.net.http.HttpResponse.BodyHandlers.ofString());

            if (tokenResp.statusCode() != 200) {
                log.error("Google token exchange failed: {}", tokenResp.body());
                return redirect("/login?error=google_auth_failed");
            }

            String accessToken = extractJsonString(tokenResp.body(), "access_token");

            // Get user info
            Map<String, String> userInfo = googleDriveService.getUserInfo(accessToken);
            String email = userInfo.get("email");
            String name = userInfo.get("name");
            String picture = userInfo.get("picture");

            if (email == null || email.isBlank()) {
                return redirect("/login?error=google_no_email");
            }

            // Find or create user
            UserModel user = userRepo.findByEmail(email).orElse(null);

            if (user == null) {
                // Auto-provision new user
                user = userFactory.createUser(email);
                user.setAccountType(UserAccountType.APP_ACCOUNT);
                user.setSignUpMethod(SignUpMethod.GOOGLE);
                user.getAccountLocks().setAccountNonDisabled(true);

                // Set profile from Google
                if (name != null && name.contains(" ")) {
                    String[] parts = name.split(" ", 2);
                    user.setFirstName(parts[0]);
                    user.setLastName(parts[1]);
                } else {
                    user.setFirstName(name);
                }
                user.setDisplayName(name);
                if (picture != null && !picture.isBlank()) user.setAvatarUrl(picture);

                // Apply default roles
                var defaults = syncService.getNewUserDefaults();
                user.setRoles(defaults.roles());
                user.setPermissions(defaults.permissions());
                user.setSensitivityClearanceLevel(defaults.sensitivityClearanceLevel());

                userRepo.save(user);
                syncService.syncForUser(user);
                user = userRepo.findByEmail(email).orElse(user);

                log.info("Auto-provisioned Google user: {} ({})", name, email);
            } else {
                // Update profile if not manually set
                if (user.getFirstName() == null && name != null) {
                    if (name.contains(" ")) {
                        String[] parts = name.split(" ", 2);
                        user.setFirstName(parts[0]);
                        user.setLastName(parts[1]);
                    } else {
                        user.setFirstName(name);
                    }
                    user.setDisplayName(name);
                }
                // Always update avatar from Google
                if (picture != null && !picture.isBlank()) user.setAvatarUrl(picture);
            }

            // Update Google identity
            UserModel.Identity identity = user.getIdentity() != null ? user.getIdentity() : new UserModel.Identity();
            identity.setProvider("GOOGLE");
            identity.setSubject(email);
            identity.setIssuer("accounts.google.com");
            identity.setUserName(name);
            user.setIdentity(identity);
            user.setExternalDirectorySource("GOOGLE");
            user.setLastLoginAt(Instant.now());
            userRepo.save(user);

            // Generate JWT using the platform's JWT utility (same signing key as AuthTokenFilter)
            String jwt = jwtUtils.generateTokenFromUsername(user);
            log.info("Google login successful for {} — redirecting to token handler", user.getEmail());

            // Redirect to the Next.js login handler which sets the cookie properly
            String encodedToken = java.net.URLEncoder.encode(jwt, java.nio.charset.StandardCharsets.UTF_8);
            return redirect("/api/login?token=" + encodedToken);

        } catch (Exception e) {
            log.error("Google login callback failed: {}", e.getMessage(), e);
            return redirect("/login?error=google_auth_failed");
        }
    }

    private ResponseEntity<String> redirect(String location) {
        return ResponseEntity.status(302)
                .header(HttpHeaders.LOCATION, location)
                .build();
    }

    private String extractJsonString(String json, String key) {
        try {
            int idx = json.indexOf("\"" + key + "\"");
            if (idx < 0) return "";
            int colon = json.indexOf(":", idx);
            int start = json.indexOf("\"", colon + 1) + 1;
            int end = json.indexOf("\"", start);
            return json.substring(start, end);
        } catch (Exception e) { return ""; }
    }
}
