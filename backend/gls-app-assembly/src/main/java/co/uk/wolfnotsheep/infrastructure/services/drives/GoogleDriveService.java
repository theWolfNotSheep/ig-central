package co.uk.wolfnotsheep.infrastructure.services.drives;

import co.uk.wolfnotsheep.document.models.ConnectedDrive;
import co.uk.wolfnotsheep.document.repositories.ConnectedDriveRepository;
import co.uk.wolfnotsheep.platform.config.services.AppConfigService;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.DriveList;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.*;

/**
 * Google Drive API wrapper. Handles file listing, downloading, metadata,
 * and token refresh for connected drives.
 *
 * OAuth credentials are read from AppConfigService (admin-configured in Settings)
 * with fallback to environment variables.
 */
@Service
public class GoogleDriveService implements co.uk.wolfnotsheep.document.services.StorageProviderService {

    private static final Logger log = LoggerFactory.getLogger(GoogleDriveService.class);
    private static final GsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();

    private final ConnectedDriveRepository driveRepo;
    private final AppConfigService configService;
    private final String fallbackClientId;
    private final String fallbackClientSecret;
    private final String fallbackRedirectUri;
    private final HttpClient httpClient;

    public GoogleDriveService(
            ConnectedDriveRepository driveRepo,
            AppConfigService configService,
            @Value("${google.oauth.client-id:}") String fallbackClientId,
            @Value("${google.oauth.client-secret:}") String fallbackClientSecret,
            @Value("${google.oauth.redirect-uri:http://localhost/api/drives/google/callback}") String fallbackRedirectUri) {
        this.driveRepo = driveRepo;
        this.configService = configService;
        this.fallbackClientId = fallbackClientId;
        this.fallbackClientSecret = fallbackClientSecret;
        this.fallbackRedirectUri = fallbackRedirectUri;
        this.httpClient = HttpClient.newHttpClient();
    }

    public String getClientId() {
        String val = configService.getValue("google.oauth.client_id", "");
        return val.isBlank() ? fallbackClientId : val;
    }

    public String getClientSecret() {
        String val = configService.getValue("google.oauth.client_secret", "");
        return val.isBlank() ? fallbackClientSecret : val;
    }

    public String getRedirectUri() {
        String val = configService.getValue("google.oauth.redirect_uri", "");
        return val.isBlank() ? fallbackRedirectUri : val;
    }

    public boolean isConfigured() {
        return !getClientId().isBlank() && !getClientSecret().isBlank();
    }

    /**
     * Generate the OAuth2 authorization URL for Google Drive.
     */
    public String getAuthorizationUrl(String state) {
        return "https://accounts.google.com/o/oauth2/v2/auth" +
                "?client_id=" + getClientId() +
                "&redirect_uri=" + getRedirectUri() +
                "&response_type=code" +
                "&scope=https://www.googleapis.com/auth/drive%20" +
                "https://www.googleapis.com/auth/drive.labels%20" +
                "https://www.googleapis.com/auth/userinfo.email%20" +
                "https://www.googleapis.com/auth/userinfo.profile" +
                "&access_type=offline" +
                "&prompt=consent" +
                "&state=" + state;
    }

    /**
     * Exchange authorization code for tokens.
     */
    public TokenResponse exchangeCode(String code) throws IOException, InterruptedException {
        String body = "code=" + code +
                "&client_id=" + getClientId() +
                "&client_secret=" + getClientSecret() +
                "&redirect_uri=" + getRedirectUri() +
                "&grant_type=authorization_code";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://oauth2.googleapis.com/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Token exchange failed: " + response.body());
        }

        // Simple JSON parsing
        String json = response.body();
        String accessToken = extractJsonString(json, "access_token");
        String refreshToken = extractJsonString(json, "refresh_token");
        int expiresIn = extractJsonInt(json, "expires_in");
        String scope = extractJsonString(json, "scope");

        return new TokenResponse(accessToken, refreshToken, expiresIn, scope);
    }

    /**
     * Get user info from the Google API.
     */
    public Map<String, String> getUserInfo(String accessToken) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://www.googleapis.com/oauth2/v2/userinfo"))
                .header("Authorization", "Bearer " + accessToken)
                .GET().build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        String json = response.body();

        return Map.of(
                "email", extractJsonString(json, "email"),
                "name", extractJsonString(json, "name"),
                "picture", extractJsonString(json, "picture")
        );
    }

    /**
     * List shared drives the user has access to.
     */
    public List<SharedDriveInfo> listSharedDrives(ConnectedDrive drive) throws Exception {
        Drive service = buildDriveService(drive);
        DriveList result = service.drives().list().setPageSize(50).execute();

        List<SharedDriveInfo> shared = new ArrayList<>();
        if (result.getDrives() != null) {
            for (var d : result.getDrives()) {
                shared.add(new SharedDriveInfo(d.getId(), d.getName()));
            }
        }
        return shared;
    }

    /**
     * Count all non-folder, non-trashed files visible to the user across
     * My Drive and shared drives. Uses minimal fields for speed.
     */
    public long countAllFiles(ConnectedDrive drive) throws Exception {
        Drive service = buildDriveService(drive);
        String query = "mimeType != 'application/vnd.google-apps.folder' and trashed = false";
        long count = 0;
        String pageToken = null;
        do {
            FileList result = service.files().list()
                    .setQ(query)
                    .setFields("nextPageToken,files(id)")
                    .setPageSize(1000)
                    .setSupportsAllDrives(true)
                    .setIncludeItemsFromAllDrives(true)
                    .setPageToken(pageToken)
                    .execute();
            if (result.getFiles() != null) count += result.getFiles().size();
            pageToken = result.getNextPageToken();
        } while (pageToken != null);
        return count;
    }

    /**
     * List files in a folder (or root). Supports shared drives.
     */
    public List<DriveFileInfo> listFilesInternal(ConnectedDrive drive, String folderId) throws Exception {
        Drive service = buildDriveService(drive);

        String query = folderId != null && !folderId.equals("root")
                ? "'" + folderId + "' in parents and trashed = false"
                : "'root' in parents and trashed = false";

        List<DriveFileInfo> files = new ArrayList<>();
        String pageToken = null;
        do {
            var req = service.files().list()
                    .setQ(query)
                    .setFields("nextPageToken,files(id,name,mimeType,size,modifiedTime,owners,webViewLink,iconLink,driveId,properties)")
                    .setPageSize(200)
                    .setOrderBy("folder,name")
                    .setCorpora("allDrives")
                    .setSupportsAllDrives(true)
                    .setIncludeItemsFromAllDrives(true);
            if (pageToken != null) req.setPageToken(pageToken);

            FileList result = req.execute();

            if (result.getFiles() != null) {
                for (File f : result.getFiles()) {
                    String ownerEmail = f.getOwners() != null && !f.getOwners().isEmpty()
                            ? f.getOwners().getFirst().getEmailAddress() : null;
                    Map<String, String> props = f.getProperties() != null ? f.getProperties() : Map.of();
                    files.add(new DriveFileInfo(
                            f.getId(), f.getName(), f.getMimeType(),
                            f.getSize() != null ? f.getSize() : 0,
                            f.getModifiedTime() != null ? f.getModifiedTime().toString() : null,
                            ownerEmail, f.getWebViewLink(), f.getIconLink(),
                            "application/vnd.google-apps.folder".equals(f.getMimeType()),
                            props.get("ig_central_status"),
                            props.get("ig_central_category"),
                            props.get("ig_central_sensitivity")
                    ));
                }
            }
            pageToken = result.getNextPageToken();
        } while (pageToken != null);

        return files;
    }

    /**
     * Get a single file's metadata.
     */
    public DriveFileInfo getFile(ConnectedDrive drive, String fileId) throws Exception {
        Drive service = buildDriveService(drive);
        File f = service.files().get(fileId)
                .setFields("id,name,mimeType,size,modifiedTime,owners,webViewLink,iconLink,properties")
                .setSupportsAllDrives(true)
                .execute();

        String ownerEmail = f.getOwners() != null && !f.getOwners().isEmpty()
                ? f.getOwners().getFirst().getEmailAddress() : null;
        Map<String, String> props = f.getProperties() != null ? f.getProperties() : Map.of();

        return new DriveFileInfo(
                f.getId(), f.getName(), f.getMimeType(),
                f.getSize() != null ? f.getSize() : 0,
                f.getModifiedTime() != null ? f.getModifiedTime().toString() : null,
                ownerEmail, f.getWebViewLink(), f.getIconLink(),
                "application/vnd.google-apps.folder".equals(f.getMimeType()),
                props.get("ig_central_status"),
                props.get("ig_central_category"),
                props.get("ig_central_sensitivity")
        );
    }

    /**
     * Download file content as an InputStream.
     * For Google Docs/Sheets/Slides, exports as PDF.
     */
    public InputStream downloadContent(ConnectedDrive drive, String fileId, String mimeType) throws Exception {
        Drive service = buildDriveService(drive);

        if (mimeType != null && mimeType.startsWith("application/vnd.google-apps.")) {
            // Google native format — export as PDF
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            service.files().export(fileId, "application/pdf").executeMediaAndDownloadTo(baos);
            return new java.io.ByteArrayInputStream(baos.toByteArray());
        }

        // Regular file — direct download
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        service.files().get(fileId).executeMediaAndDownloadTo(baos);
        return new java.io.ByteArrayInputStream(baos.toByteArray());
    }

    /**
     * Refresh access token if expired.
     */
    public ConnectedDrive refreshTokenIfNeeded(ConnectedDrive drive) throws IOException {
        if (drive.getTokenExpiresAt() != null && drive.getTokenExpiresAt().isAfter(Instant.now().plusSeconds(60))) {
            return drive; // Still valid
        }

        try {
            String body = "client_id=" + getClientId() +
                    "&client_secret=" + getClientSecret() +
                    "&refresh_token=" + drive.getRefreshToken() +
                    "&grant_type=refresh_token";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://oauth2.googleapis.com/token"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                String newToken = extractJsonString(response.body(), "access_token");
                int expiresIn = extractJsonInt(response.body(), "expires_in");
                drive.setAccessToken(newToken);
                drive.setTokenExpiresAt(Instant.now().plusSeconds(expiresIn));
                driveRepo.save(drive);
                log.debug("Refreshed Google Drive token for {}", drive.getProviderAccountEmail());
            } else {
                log.error("Google Drive token refresh failed (HTTP {}): {}", response.statusCode(), response.body());
                throw new IOException("Token refresh failed — user may need to reconnect their Drive");
            }
        } catch (IOException e) {
            throw e; // Propagate — don't swallow
        } catch (Exception e) {
            throw new IOException("Token refresh failed: " + e.getMessage(), e);
        }

        return drive;
    }

    private Drive buildDriveService(ConnectedDrive drive) throws GeneralSecurityException, IOException {
        drive = refreshTokenIfNeeded(drive);

        HttpTransport transport = GoogleNetHttpTransport.newTrustedTransport();

        // Use UserCredentials with refresh token so the Google client can auto-refresh if needed
        GoogleCredentials credentials;
        if (drive.getRefreshToken() != null && !drive.getRefreshToken().isBlank()) {
            credentials = com.google.auth.oauth2.UserCredentials.newBuilder()
                    .setClientId(getClientId())
                    .setClientSecret(getClientSecret())
                    .setRefreshToken(drive.getRefreshToken())
                    .setAccessToken(new AccessToken(drive.getAccessToken(), Date.from(drive.getTokenExpiresAt())))
                    .build();
        } else {
            credentials = GoogleCredentials.create(
                    new AccessToken(drive.getAccessToken(), Date.from(drive.getTokenExpiresAt())));
        }

        return new Drive.Builder(transport, JSON_FACTORY, new HttpCredentialsAdapter(credentials))
                .setApplicationName("IG Central")
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

    private int extractJsonInt(String json, String key) {
        try {
            int idx = json.indexOf("\"" + key + "\"");
            if (idx < 0) return 3600;
            int colon = json.indexOf(":", idx);
            int end = json.indexOf(",", colon);
            if (end < 0) end = json.indexOf("}", colon);
            return Integer.parseInt(json.substring(colon + 1, end).trim());
        } catch (Exception e) { return 3600; }
    }

    /**
     * Write IG Central classification properties back to a Google Drive file.
     */
    public void writeClassificationProperties(ConnectedDrive drive, String fileId,
                                               String status, String category,
                                               String sensitivity, String documentId) throws Exception {
        Drive service = buildDriveService(drive);

        File fileMetadata = new File();
        Map<String, String> properties = new HashMap<>();
        properties.put("ig_central_status", status);
        properties.put("ig_central_category", category != null ? category : "");
        properties.put("ig_central_sensitivity", sensitivity != null ? sensitivity : "");
        properties.put("ig_central_document_id", documentId);
        properties.put("ig_central_classified_at", Instant.now().toString());
        fileMetadata.setProperties(properties);

        service.files().update(fileId, fileMetadata)
                .setSupportsAllDrives(true)
                .execute();

        log.debug("Wrote classification properties to Drive file {}", fileId);
    }

    /**
     * List only folders in a given parent (for building folder tree).
     */
    public List<DriveFileInfo> listFoldersInternal(ConnectedDrive drive, String parentId) throws Exception {
        Drive service = buildDriveService(drive);

        String query = "'" + (parentId != null ? parentId : "root") + "' in parents and " +
                "mimeType = 'application/vnd.google-apps.folder' and trashed = false";

        List<DriveFileInfo> folders = new ArrayList<>();
        String pageToken = null;
        do {
            var req = service.files().list()
                    .setQ(query)
                    .setFields("nextPageToken,files(id,name,mimeType)")
                    .setPageSize(200)
                    .setOrderBy("name")
                    .setCorpora("allDrives")
                    .setSupportsAllDrives(true)
                    .setIncludeItemsFromAllDrives(true);
            if (pageToken != null) req.setPageToken(pageToken);

            FileList result = req.execute();

            if (result.getFiles() != null) {
                for (File f : result.getFiles()) {
                    folders.add(new DriveFileInfo(f.getId(), f.getName(), f.getMimeType(),
                            0, null, null, null, null, true, null, null, null));
                }
            }
            pageToken = result.getNextPageToken();
        } while (pageToken != null);

        return folders;
    }

    /**
     * Get info about a single file by ID.
     */
    public DriveFileInfo getFileInfoInternal(ConnectedDrive drive, String fileId) throws Exception {
        com.google.api.services.drive.Drive service = buildDriveService(drive);
        var file = service.files().get(fileId)
                .setFields("id,name,mimeType,size,modifiedTime,owners,webViewLink,properties")
                .setSupportsAllDrives(true)
                .execute();

        String ownerEmail = file.getOwners() != null && !file.getOwners().isEmpty()
                ? file.getOwners().get(0).getEmailAddress() : null;
        Map<String, String> props = file.getProperties() != null ? file.getProperties() : Map.of();

        return new DriveFileInfo(
                file.getId(), file.getName(), file.getMimeType(),
                file.getSize() != null ? file.getSize() : 0,
                file.getModifiedTime() != null ? file.getModifiedTime().toStringRfc3339() : null,
                ownerEmail,
                file.getWebViewLink(), null,
                file.getMimeType() != null && file.getMimeType().equals("application/vnd.google-apps.folder"),
                props.getOrDefault("ig_central_status", ""),
                props.getOrDefault("ig_central_category", ""),
                props.getOrDefault("ig_central_sensitivity", ""));
    }

    // ── StorageProviderService interface implementation ──────

    @Override
    public co.uk.wolfnotsheep.document.models.StorageProviderType getType() {
        return co.uk.wolfnotsheep.document.models.StorageProviderType.GOOGLE_DRIVE;
    }

    @Override
    public List<FileEntry> listFiles(ConnectedDrive drive, String folderId) throws Exception {
        return listFilesNative(drive, folderId).stream().map(this::toFileEntry).toList();
    }

    @Override
    public List<FileEntry> listFolders(ConnectedDrive drive, String parentId) throws Exception {
        return listFoldersNative(drive, parentId).stream()
                .map(f -> FileEntry.folder(f.id(), f.name())).toList();
    }

    @Override
    public InputStream downloadContent(ConnectedDrive drive, String fileId) throws Exception {
        // Get file info to determine mime type for export
        var info = getFileInfoNative(drive, fileId);
        return downloadContent(drive, fileId, info != null ? info.mimeType() : "application/octet-stream");
    }

    @Override
    public FileEntry getFileInfo(ConnectedDrive drive, String fileId) throws Exception {
        DriveFileInfo info = getFileInfoNative(drive, fileId);
        return info != null ? toFileEntry(info) : null;
    }

    @Override
    public void uploadFile(ConnectedDrive drive, String folderId, String fileName,
                           InputStream content, long size, String contentType) throws Exception {
        // TODO: implement Google Drive upload
        throw new UnsupportedOperationException("Google Drive upload not yet implemented");
    }

    @Override
    public boolean supportsOAuth() { return true; }

    @Override
    public String getAuthorizationUrl(ConnectedDrive drive, String state) {
        return getAuthorizationUrl(state);
    }

    private FileEntry toFileEntry(DriveFileInfo f) {
        return new FileEntry(f.id(), f.name(), f.mimeType(), f.size(),
                f.modifiedTime() != null ? java.time.Instant.parse(f.modifiedTime()) : null,
                f.ownerEmail(), f.webViewLink(), f.folder(),
                java.util.Map.of(
                        "igStatus", f.igStatus() != null ? f.igStatus() : "",
                        "igCategory", f.igCategory() != null ? f.igCategory() : "",
                        "igSensitivity", f.igSensitivity() != null ? f.igSensitivity() : ""));
    }

    // Keep the native methods with their original names for backward compat
    public List<DriveFileInfo> listFilesNative(ConnectedDrive drive, String folderId) throws Exception {
        return listFilesInternal(drive, folderId);
    }

    public List<DriveFileInfo> listFoldersNative(ConnectedDrive drive, String parentId) throws Exception {
        return listFoldersInternal(drive, parentId);
    }

    public DriveFileInfo getFileInfoNative(ConnectedDrive drive, String fileId) throws Exception {
        return getFileInfoInternal(drive, fileId);
    }

    public record TokenResponse(String accessToken, String refreshToken, int expiresIn, String scope) {}

    public record DriveFileInfo(String id, String name, String mimeType, long size,
                                String modifiedTime, String ownerEmail, String webViewLink,
                                String iconLink, boolean folder,
                                String igStatus, String igCategory, String igSensitivity) {}

    public record SharedDriveInfo(String id, String name) {}
}
