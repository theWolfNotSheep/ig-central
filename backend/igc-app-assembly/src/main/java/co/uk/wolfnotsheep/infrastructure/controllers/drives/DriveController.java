package co.uk.wolfnotsheep.infrastructure.controllers.drives;

import co.uk.wolfnotsheep.document.events.DocumentIngestedEvent;
import co.uk.wolfnotsheep.document.models.ConnectedDrive;
import co.uk.wolfnotsheep.document.models.DocumentModel;
import co.uk.wolfnotsheep.document.models.DocumentStatus;
import co.uk.wolfnotsheep.document.repositories.ConnectedDriveRepository;
import co.uk.wolfnotsheep.document.repositories.DocumentRepository;
import co.uk.wolfnotsheep.document.services.DocumentService;
import co.uk.wolfnotsheep.document.services.ObjectStorageService;
import co.uk.wolfnotsheep.document.services.StorageProviderService;
import co.uk.wolfnotsheep.document.util.SlugGenerator;
import co.uk.wolfnotsheep.infrastructure.services.drives.StorageProviderRegistry;
import co.uk.wolfnotsheep.infrastructure.services.drives.GoogleDriveService;
import co.uk.wolfnotsheep.infrastructure.services.drives.GoogleDriveService.DriveFileInfo;
import co.uk.wolfnotsheep.platform.config.services.AppConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.io.InputStream;
import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/drives")
public class DriveController {

    private static final Logger log = LoggerFactory.getLogger(DriveController.class);
    private static final String EXCHANGE = "igc.documents";
    private static final String ROUTING_INGESTED = "document.ingested";

    private final GoogleDriveService googleDriveService;
    private final ConnectedDriveRepository driveRepo;
    private final DocumentRepository documentRepo;
    private final DocumentService documentService;
    private final ObjectStorageService objectStorage;
    private final RabbitTemplate rabbitTemplate;
    private final StorageProviderRegistry providerRegistry;
    private final AppConfigService appConfigService;
    private final co.uk.wolfnotsheep.infrastructure.services.PipelineThrottleService throttleService;

    public DriveController(GoogleDriveService googleDriveService,
                           ConnectedDriveRepository driveRepo,
                           DocumentRepository documentRepo,
                           DocumentService documentService,
                           ObjectStorageService objectStorage,
                           RabbitTemplate rabbitTemplate,
                           StorageProviderRegistry providerRegistry,
                           AppConfigService appConfigService,
                           co.uk.wolfnotsheep.infrastructure.services.PipelineThrottleService throttleService) {
        this.googleDriveService = googleDriveService;
        this.driveRepo = driveRepo;
        this.documentRepo = documentRepo;
        this.documentService = documentService;
        this.objectStorage = objectStorage;
        this.rabbitTemplate = rabbitTemplate;
        this.providerRegistry = providerRegistry;
        this.appConfigService = appConfigService;
        this.throttleService = throttleService;
    }

    // ── Config Check ────────────────────────────────────

    @GetMapping("/config-status")
    public ResponseEntity<Map<String, Object>> getConfigStatus() {
        return ResponseEntity.ok(Map.of(
                "configured", googleDriveService.isConfigured(),
                "provider", "GOOGLE_DRIVE"
        ));
    }

    // ── OAuth2 Flow ──────────────────────────────────────

    @GetMapping("/google/auth-url")
    public ResponseEntity<Map<String, String>> getAuthUrl(@AuthenticationPrincipal UserDetails user) {
        if (!googleDriveService.isConfigured()) {
            return ResponseEntity.badRequest().body(Map.of("error",
                    "Google Drive not configured. Admin must set OAuth credentials in Settings."));
        }
        String state = Base64.getEncoder().encodeToString(user.getUsername().getBytes());
        String url = googleDriveService.getAuthorizationUrl(state);
        return ResponseEntity.ok(Map.of("url", url));
    }

    @GetMapping("/google/callback")
    public ResponseEntity<String> handleCallback(
            @RequestParam String code,
            @RequestParam String state) {
        try {
            String decoded = new String(Base64.getDecoder().decode(state));
            boolean isGmail = decoded.startsWith("GMAIL:");
            String email = isGmail ? decoded.substring(6) : decoded;

            var tokens = googleDriveService.exchangeCode(code);
            var userInfo = googleDriveService.getUserInfo(tokens.accessToken());

            String provider = isGmail ? "GMAIL" : "GOOGLE_DRIVE";
            Optional<ConnectedDrive> existing = driveRepo.findByUserIdAndProviderAndProviderAccountEmail(
                    email, provider, userInfo.get("email"));
            if (existing.isEmpty()) {
                // Fallback: try legacy lookup without provider filter
                existing = driveRepo.findByUserIdAndProviderAccountEmail(email, userInfo.get("email"));
                if (existing.isPresent() && !provider.equals(existing.get().getProvider())) {
                    existing = Optional.empty(); // Different provider — create new
                }
            }

            ConnectedDrive drive;
            if (existing.isPresent()) {
                drive = existing.get();
            } else {
                drive = new ConnectedDrive();
                drive.setUserId(email);
                drive.setProvider(provider);
                if (isGmail) {
                    drive.setProviderType(co.uk.wolfnotsheep.document.models.StorageProviderType.GMAIL);
                    drive.setDisplayName(userInfo.get("name") + " (Gmail)");
                }
                drive.setProviderAccountEmail(userInfo.get("email"));
                drive.setProviderAccountName(userInfo.get("name"));
                drive.setConnectedAt(Instant.now());
                if (!isGmail) drive.setMonitoredFolderIds(List.of());
            }

            drive.setAccessToken(tokens.accessToken());
            drive.setRefreshToken(tokens.refreshToken() != null ? tokens.refreshToken() : drive.getRefreshToken());
            drive.setTokenExpiresAt(Instant.now().plusSeconds(tokens.expiresIn()));
            if (tokens.scope() != null && !tokens.scope().isBlank()) {
                drive.setGrantedScopes(tokens.scope());
            }
            drive.setActive(true);
            driveRepo.save(drive);

            String messageType = isGmail ? "gmail-connected" : "google-drive-connected";
            String fallbackUrl = isGmail ? "/mailboxes?connected=true" : "/drives?connected=true";

            log.info("{} connected for user {} ({})", provider, email, userInfo.get("email"));

            // Return HTML that closes the popup and refreshes the parent
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, "text/html")
                    .body("<html><body><script>" +
                        "if (window.opener) {" +
                        "  window.opener.postMessage({ type: '" + messageType + "' }, '*');" +
                        "  window.close();" +
                        "} else {" +
                        "  window.location.href = '" + fallbackUrl + "';" +
                        "}" +
                        "</script><p>Connected! You can close this window.</p></body></html>");

        } catch (Exception e) {
            log.error("Google OAuth callback failed: {}", e.getMessage(), e);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, "text/html")
                    .body("""
                        <html><body><script>
                        if (window.opener) {
                            window.opener.postMessage({ type: 'google-drive-error', error: 'auth_failed' }, '*');
                            window.close();
                        } else {
                            window.location.href = '/drives?error=auth_failed';
                        }
                        </script><p>Connection failed. You can close this window.</p></body></html>
                    """);
        }
    }

    // ── Connected Drives ─────────────────────────────────

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listDrives(@AuthenticationPrincipal UserDetails user) {
        // Return user's drives + system drives (Local Storage)
        List<ConnectedDrive> drives = driveRepo.findAccessibleDrives(user.getUsername());
        List<Map<String, Object>> result = drives.stream().map(d -> {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("id", d.getId());
            m.put("userId", d.getUserId());
            m.put("provider", d.getProvider());
            m.put("providerType", d.getProviderType() != null ? d.getProviderType().name() : d.getProvider());
            m.put("displayName", d.getDisplayName() != null ? d.getDisplayName() : d.getProviderAccountEmail());
            m.put("providerAccountEmail", d.getProviderAccountEmail());
            m.put("providerAccountName", d.getProviderAccountName());
            m.put("monitoredFolderIds", d.getMonitoredFolderIds());
            m.put("systemDrive", d.isSystemDrive());
            m.put("active", d.isActive());
            m.put("connectedAt", d.getConnectedAt());
            m.put("lastSyncAt", d.getLastSyncAt());
            m.put("hasWriteAccess", d.hasWriteAccess());
            m.put("needsReconnect", d.needsReconnect());
            return m;
        }).toList();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/stats")
    public ResponseEntity<List<Map<String, Object>>> driveStats(@AuthenticationPrincipal UserDetails user) {
        List<ConnectedDrive> drives = driveRepo.findAccessibleDrives(user.getUsername());
        List<DocumentStatus> classifiedStatuses = List.of(
                DocumentStatus.CLASSIFIED, DocumentStatus.GOVERNANCE_APPLIED,
                DocumentStatus.INBOX, DocumentStatus.FILED);
        List<DocumentStatus> inProgressStatuses = List.of(
                DocumentStatus.UPLOADED, DocumentStatus.PROCESSING,
                DocumentStatus.PROCESSED, DocumentStatus.CLASSIFYING);
        List<DocumentStatus> failedStatuses = List.of(
                DocumentStatus.PROCESSING_FAILED, DocumentStatus.CLASSIFICATION_FAILED,
                DocumentStatus.ENFORCEMENT_FAILED);

        List<Map<String, Object>> result = drives.stream().map(d -> {
            long classified = documentRepo.countByConnectedDriveIdAndStatusIn(d.getId(), classifiedStatuses);
            long inProgress = documentRepo.countByConnectedDriveIdAndStatusIn(d.getId(), inProgressStatuses);
            long failed = documentRepo.countByConnectedDriveIdAndStatusIn(d.getId(), failedStatuses);
            long tracked = documentRepo.countByConnectedDriveId(d.getId());

            // For external drives, get the real file count from the provider
            long driveFileCount = tracked;
            if (!d.isSystemDrive()) {
                try {
                    driveFileCount = googleDriveService.countAllFiles(d);
                } catch (Exception e) {
                    log.warn("Could not count files in drive {}: {}", d.getId(), e.getMessage());
                    driveFileCount = tracked; // fallback to tracked count
                }
            }
            long unclassified = driveFileCount - classified - inProgress - failed;

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("driveId", d.getId());
            m.put("displayName", d.getDisplayName() != null ? d.getDisplayName() : d.getProviderAccountEmail());
            m.put("provider", d.getProviderType() != null ? d.getProviderType().name() : d.getProvider());
            m.put("systemDrive", d.isSystemDrive());
            m.put("total", driveFileCount);
            m.put("classified", classified);
            m.put("inProgress", inProgress);
            m.put("failed", failed);
            m.put("unclassified", Math.max(0, unclassified));
            return m;
        }).toList();
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/{driveId}")
    public ResponseEntity<Void> disconnect(@PathVariable String driveId, @AuthenticationPrincipal UserDetails user) {
        return driveRepo.findById(driveId)
                .filter(d -> d.getUserId().equals(user.getUsername()))
                .map(d -> {
                    d.setActive(false);
                    driveRepo.save(d);
                    return ResponseEntity.ok().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ── Shared Drives ─────────────────────────────────────

    @GetMapping("/{driveId}/shared-drives")
    public ResponseEntity<List<GoogleDriveService.SharedDriveInfo>> listSharedDrives(
            @PathVariable String driveId,
            @AuthenticationPrincipal UserDetails user) {
        try {
            ConnectedDrive drive = getAuthorisedDrive(driveId, user.getUsername());
            if (drive == null) return ResponseEntity.notFound().build();
            return ResponseEntity.ok(googleDriveService.listSharedDrives(drive));
        } catch (Exception e) {
            log.error("Failed to list shared drives: {}", e.getMessage());
            return ResponseEntity.status(502).body(List.of());
        }
    }

    // ── Folder Tree ────────────────────────────────────────

    @GetMapping("/{driveId}/folders")
    public ResponseEntity<List<?>> listFolders(
            @PathVariable String driveId,
            @RequestParam(defaultValue = "root") String parentId,
            @AuthenticationPrincipal UserDetails user) {
        try {
            ConnectedDrive drive = getAuthorisedDrive(driveId, user.getUsername());
            if (drive == null) return ResponseEntity.notFound().build();

            StorageProviderService provider = providerRegistry.get(drive);
            List<StorageProviderService.FileEntry> folders = provider.listFolders(drive, "root".equals(parentId) ? null : parentId);

            // Convert to a consistent response format
            return ResponseEntity.ok(folders.stream().map(f -> Map.of(
                    "id", f.id(), "name", f.name(), "folder", true
            )).toList());
        } catch (Exception e) {
            log.error("Failed to list folders for drive {}: {}", driveId, e.getMessage());
            return ResponseEntity.status(502).body(List.of());
        }
    }

    // ── Folder Monitoring ─────────────────────────────────

    @PostMapping("/{driveId}/monitor/{folderId}")
    public ResponseEntity<ConnectedDrive> monitorFolder(
            @PathVariable String driveId, @PathVariable String folderId,
            @AuthenticationPrincipal UserDetails user) {
        ConnectedDrive drive = getAuthorisedDrive(driveId, user.getUsername());
        if (drive == null) return ResponseEntity.notFound().build();

        List<String> monitored = drive.getMonitoredFolderIds() != null
                ? new ArrayList<>(drive.getMonitoredFolderIds()) : new ArrayList<>();
        if (!monitored.contains(folderId)) monitored.add(folderId);
        drive.setMonitoredFolderIds(monitored);
        driveRepo.save(drive);

        // Strip tokens from response
        drive.setAccessToken(null); drive.setRefreshToken(null);
        return ResponseEntity.ok(drive);
    }

    @DeleteMapping("/{driveId}/monitor/{folderId}")
    public ResponseEntity<ConnectedDrive> unmonitorFolder(
            @PathVariable String driveId, @PathVariable String folderId,
            @AuthenticationPrincipal UserDetails user) {
        ConnectedDrive drive = getAuthorisedDrive(driveId, user.getUsername());
        if (drive == null) return ResponseEntity.notFound().build();

        if (drive.getMonitoredFolderIds() != null) {
            drive.setMonitoredFolderIds(drive.getMonitoredFolderIds().stream()
                    .filter(id -> !id.equals(folderId)).toList());
            driveRepo.save(drive);
        }

        drive.setAccessToken(null); drive.setRefreshToken(null);
        return ResponseEntity.ok(drive);
    }

    // ── File Browser ─────────────────────────────────────

    @GetMapping("/{driveId}/files")
    public ResponseEntity<List<Map<String, Object>>> listFiles(
            @PathVariable String driveId,
            @RequestParam(defaultValue = "root") String folderId,
            @AuthenticationPrincipal UserDetails user) {
        try {
            ConnectedDrive drive = getAuthorisedDrive(driveId, user.getUsername());
            if (drive == null) return ResponseEntity.notFound().build();

            StorageProviderService provider = providerRegistry.get(drive);
            List<StorageProviderService.FileEntry> entries = provider.listFiles(drive, "root".equals(folderId) ? null : folderId);

            // For external providers, also get folders
            List<StorageProviderService.FileEntry> folders = List.of();
            try { folders = provider.listFolders(drive, "root".equals(folderId) ? null : folderId); }
            catch (Exception e) { log.warn("Failed to list folders alongside files for drive {}: {}", driveId, e.getMessage()); }

            // Build response — folders first, then files
            List<Map<String, Object>> result = new ArrayList<>();

            for (var f : folders) {
                result.add(Map.of("id", f.id(), "name", f.name(), "mimeType", "application/vnd.folder",
                        "size", 0, "folder", true));
            }

            // For external drives, enrich with tracking info — query only matching fileIds
            if (!drive.isSystemDrive()) {
                List<String> fileIds = entries.stream().map(StorageProviderService.FileEntry::id).toList();
                Map<String, DocumentModel> trackedByFileId = new java.util.HashMap<>();
                if (!fileIds.isEmpty()) {
                    for (DocumentModel doc : documentService.findByExternalFileIds(fileIds)) {
                        if (doc.getExternalStorageRef() != null) {
                            String fid = doc.getExternalStorageRef().get("fileId");
                            if (fid != null) trackedByFileId.put(fid, doc);
                        }
                    }
                }

                for (var f : entries) {
                    Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("id", f.id()); m.put("name", f.name()); m.put("mimeType", f.mimeType());
                    m.put("size", f.size()); m.put("folder", false);
                    m.put("modifiedTime", f.modifiedTime() != null ? f.modifiedTime().toString() : null);
                    m.put("ownerEmail", f.ownerEmail()); m.put("webViewLink", f.webViewLink());
                    if (f.metadata() != null) {
                        m.put("igStatus", f.metadata().getOrDefault("igStatus", ""));
                        m.put("igCategory", f.metadata().getOrDefault("igCategory", ""));
                        m.put("igSensitivity", f.metadata().getOrDefault("igSensitivity", ""));
                    }
                    DocumentModel tracked = trackedByFileId.get(f.id());
                    if (tracked != null) {
                        m.put("tracked", true);
                        m.put("trackedStatus", tracked.getStatus() != null ? tracked.getStatus().name() : null);
                        m.put("trackedCategory", tracked.getCategoryName());
                        m.put("trackedSensitivity", tracked.getSensitivityLabel() != null ? tracked.getSensitivityLabel().name() : null);
                        m.put("trackedDocId", tracked.getId()); m.put("trackedSlug", tracked.getSlug());
                    } else { m.put("tracked", false); }
                    result.add(m);
                }
            } else {
                // Local storage — files ARE documents, already have classification data
                for (var f : entries) {
                    Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("id", f.id()); m.put("name", f.name()); m.put("mimeType", f.mimeType());
                    m.put("size", f.size()); m.put("folder", false);
                    m.put("modifiedTime", f.modifiedTime() != null ? f.modifiedTime().toString() : null);
                    m.put("tracked", true);
                    m.put("metadata", f.metadata());
                    if (f.metadata() != null) {
                        m.put("trackedStatus", f.metadata().getOrDefault("status", ""));
                        m.put("trackedCategory", f.metadata().getOrDefault("categoryName", ""));
                        m.put("trackedSensitivity", f.metadata().getOrDefault("sensitivityLabel", ""));
                    }
                    result.add(m);
                }
            }

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Failed to list files: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    // ── Register Files for Classification ────────────────

    @PostMapping("/{driveId}/register")
    public ResponseEntity<Map<String, Object>> registerFiles(
            @PathVariable String driveId,
            @RequestBody RegisterRequest request,
            @AuthenticationPrincipal UserDetails user) {
        try {
            ConnectedDrive drive = getAuthorisedDrive(driveId, user.getUsername());
            if (drive == null) return ResponseEntity.notFound().build();

            int registered = 0;
            List<String> fileIds = request.fileIds() != null ? request.fileIds() : List.of();

            // If a folder is specified, get all files in it
            if (request.folderId() != null && !request.folderId().isBlank()) {
                List<DriveFileInfo> folderFiles = googleDriveService.listFilesInternal(drive, request.folderId());
                for (DriveFileInfo f : folderFiles) {
                    if (!f.folder()) fileIds = new ArrayList<>(fileIds);
                    if (!f.folder() && !fileIds.contains(f.id())) {
                        ((ArrayList<String>) fileIds).add(f.id());
                    }
                }
            }

            // Throttle check
            String throttleError = throttleService.checkThrottle(fileIds.size());
            if (throttleError != null) {
                return ResponseEntity.status(429).body(Map.of(
                        "error", throttleError,
                        "available", throttleService.getAvailableSlots(),
                        "maxBatchSize", throttleService.getMaxBatchSize()
                ));
            }

            for (String fileId : fileIds) {
                try {
                    registerDriveFile(drive, fileId, user.getUsername(), request.pipelineId());
                    registered++;
                } catch (Exception e) {
                    log.warn("Failed to register Drive file {}: {}", fileId, e.getMessage());
                }
            }

            return ResponseEntity.ok(Map.of("registered", registered, "total", fileIds.size()));
        } catch (Exception e) {
            log.error("Failed to register Drive files: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    // ── Content Proxy ────────────────────────────────────

    @GetMapping("/{driveId}/content/{fileId}")
    public ResponseEntity<InputStreamResource> getContent(
            @PathVariable String driveId,
            @PathVariable String fileId,
            @AuthenticationPrincipal UserDetails user) {
        try {
            ConnectedDrive drive = getAuthorisedDrive(driveId, user.getUsername());
            if (drive == null) return ResponseEntity.notFound().build();

            DriveFileInfo info = googleDriveService.getFileInfoInternal(drive, fileId);
            InputStream content = googleDriveService.downloadContent(drive, fileId, info.mimeType());

            String contentType = info.mimeType().startsWith("application/vnd.google-apps.")
                    ? "application/pdf" : info.mimeType();

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(new InputStreamResource(content));
        } catch (Exception e) {
            log.error("Failed to get Drive file content: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    // ── Write Classification Back to Google Drive ───────

    @PostMapping("/sync-classification/{documentId}")
    public ResponseEntity<Map<String, Object>> syncClassification(
            @PathVariable String documentId,
            @AuthenticationPrincipal UserDetails user) {
        try {
            DocumentModel doc = documentService.getById(documentId);
            if (doc == null) return ResponseEntity.notFound().build();
            if (!"GOOGLE_DRIVE".equals(doc.getStorageProvider())) {
                return ResponseEntity.badRequest().body(Map.of("error", "Not a Google Drive document"));
            }

            Map<String, String> ref = doc.getExternalStorageRef();
            if (ref == null || ref.get("driveId") == null || ref.get("fileId") == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Missing Drive reference"));
            }

            ConnectedDrive drive = getAuthorisedDrive(ref.get("driveId"), user.getUsername());
            if (drive == null) return ResponseEntity.notFound().build();

            googleDriveService.writeClassificationProperties(drive, ref.get("fileId"),
                    doc.getStatus() != null ? doc.getStatus().name() : "",
                    doc.getCategoryName(),
                    doc.getSensitivityLabel() != null ? doc.getSensitivityLabel().name() : "",
                    doc.getId());

            return ResponseEntity.ok(Map.of("synced", true, "fileId", ref.get("fileId")));
        } catch (Exception e) {
            log.error("Failed to sync classification to Drive: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    // ── Helpers ──────────────────────────────────────────

    private ConnectedDrive getAuthorisedDrive(String driveId, String username) {
        return driveRepo.findById(driveId)
                .filter(d -> d.isActive() && (d.isSystemDrive() || username.equals(d.getUserId())))
                .orElse(null);
    }

    private void registerDriveFile(ConnectedDrive drive, String fileId, String username, String pipelineId) throws Exception {
        DriveFileInfo info = googleDriveService.getFileInfoInternal(drive, fileId);
        if (info.folder()) return;

        // Create document record
        DocumentModel doc = new DocumentModel();
        doc.setFileName(info.name());
        doc.setOriginalFileName(info.name());
        doc.setMimeType(info.mimeType().startsWith("application/vnd.google-apps.")
                ? "application/pdf" : info.mimeType());
        doc.setFileSizeBytes(info.size());
        doc.setStorageProvider("GOOGLE_DRIVE");
        doc.setExternalStorageRef(Map.of(
                "fileId", info.id(),
                "driveId", drive.getId(),
                "webViewLink", info.webViewLink() != null ? info.webViewLink() : "",
                "ownerEmail", info.ownerEmail() != null ? info.ownerEmail() : "",
                "providerAccountEmail", drive.getProviderAccountEmail()
        ));
        doc.setConnectedDriveId(drive.getId());
        doc.setStatus(DocumentStatus.UPLOADED);
        doc.setUploadedBy(username);
        doc.setCreatedAt(Instant.now());
        doc.setUpdatedAt(Instant.now());
        if (pipelineId != null) {
            doc.setPipelineId(pipelineId);
            doc.setPipelineSelectionMethod("MANUAL");
        }

        doc = documentService.save(doc);
        doc.setSlug(SlugGenerator.generate(info.name(), doc.getId()));
        doc = documentService.save(doc);

        // Storage mode: "cache" downloads to MinIO, "stream" processes directly from Drive
        String storageMode = appConfigService.getValue("drives.storage_mode", "cache");
        String storageKey = null;

        if ("cache".equals(storageMode)) {
            InputStream content = googleDriveService.downloadContent(drive, fileId, info.mimeType());
            storageKey = doc.getId() + "-" + sanitizeStorageKey(info.name());
            doc.setStorageBucket("igc-documents");
            doc.setStorageKey(storageKey);
            documentService.save(doc);

            byte[] bytes = content.readAllBytes();
            objectStorage.upload(storageKey, new java.io.ByteArrayInputStream(bytes), bytes.length, doc.getMimeType());
        } else {
            log.info("Stream mode — skipping MinIO cache for Drive file: {}", info.name());
        }

        // Queue for processing (storageBucket/storageKey may be null in stream mode)
        try {
            rabbitTemplate.convertAndSend(EXCHANGE, ROUTING_INGESTED, new DocumentIngestedEvent(
                    doc.getId(), info.name(), doc.getMimeType(), info.size(),
                    doc.getStorageBucket(), doc.getStorageKey(), username, Instant.now(),
                    pipelineId
            ));
        } catch (Exception e) {
            log.error("Failed to queue Drive file {} for processing: {}", info.name(), e.getMessage());
            documentService.setError(doc.getId(), DocumentStatus.PROCESSING_FAILED, "QUEUE", e.getMessage());
        }

        log.info("Registered Google Drive file: {} ({})", info.name(), fileId);
    }

    /** Strip characters unsafe for object storage keys (S3/MinIO). */
    private static String sanitizeStorageKey(String name) {
        if (name == null) return "unknown";
        return name.replaceAll("[/\\\\:*?\"<>|,]", "_").replaceAll("\\s+", " ").trim();
    }

    record RegisterRequest(List<String> fileIds, String folderId, String pipelineId) {}
}
