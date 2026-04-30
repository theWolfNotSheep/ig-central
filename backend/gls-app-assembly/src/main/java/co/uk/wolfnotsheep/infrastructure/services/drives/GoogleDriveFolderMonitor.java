package co.uk.wolfnotsheep.infrastructure.services.drives;

import co.uk.wolfnotsheep.document.events.DocumentIngestedEvent;
import co.uk.wolfnotsheep.document.models.ConnectedDrive;
import co.uk.wolfnotsheep.document.models.DocumentModel;
import co.uk.wolfnotsheep.document.models.DocumentStatus;
import co.uk.wolfnotsheep.document.models.SystemError;
import co.uk.wolfnotsheep.document.repositories.ConnectedDriveRepository;
import co.uk.wolfnotsheep.document.repositories.DocumentRepository;
import co.uk.wolfnotsheep.document.repositories.SystemErrorRepository;
import co.uk.wolfnotsheep.document.services.DocumentService;
import co.uk.wolfnotsheep.document.services.ObjectStorageService;
import co.uk.wolfnotsheep.document.util.SlugGenerator;
import co.uk.wolfnotsheep.infrastructure.services.connectors.PerSourceLock;
import co.uk.wolfnotsheep.infrastructure.services.drives.GoogleDriveService.DriveFileInfo;
import co.uk.wolfnotsheep.platform.config.services.AppConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Periodically checks monitored Google Drive folders for new files
 * and auto-registers them for classification.
 */
@Service
public class GoogleDriveFolderMonitor {

    private static final Logger log = LoggerFactory.getLogger(GoogleDriveFolderMonitor.class);
    private static final String EXCHANGE = "gls.documents";
    private static final String ROUTING_INGESTED = "document.ingested";

    private final ConnectedDriveRepository driveRepo;
    private final DocumentRepository documentRepo;
    private final DocumentService documentService;
    private final GoogleDriveService googleDriveService;
    private final ObjectStorageService objectStorage;
    private final RabbitTemplate rabbitTemplate;
    private final MongoTemplate mongoTemplate;
    private final SystemErrorRepository systemErrorRepo;
    private final AppConfigService appConfigService;
    private final PerSourceLock perSourceLock;

    public GoogleDriveFolderMonitor(ConnectedDriveRepository driveRepo,
                                     DocumentRepository documentRepo,
                                     DocumentService documentService,
                                     GoogleDriveService googleDriveService,
                                     ObjectStorageService objectStorage,
                                     RabbitTemplate rabbitTemplate,
                                     MongoTemplate mongoTemplate,
                                     SystemErrorRepository systemErrorRepo,
                                     AppConfigService appConfigService,
                                     PerSourceLock perSourceLock) {
        this.driveRepo = driveRepo;
        this.documentRepo = documentRepo;
        this.documentService = documentService;
        this.googleDriveService = googleDriveService;
        this.mongoTemplate = mongoTemplate;
        this.objectStorage = objectStorage;
        this.rabbitTemplate = rabbitTemplate;
        this.systemErrorRepo = systemErrorRepo;
        this.appConfigService = appConfigService;
        this.perSourceLock = perSourceLock;
    }

    /**
     * Runs every 5 minutes. Checks all active drives with monitored folders
     * for new files that haven't been registered yet.
     */
    @Scheduled(fixedDelay = 300000, initialDelay = 60000) // 5 min
    public void checkMonitoredFolders() {
        List<ConnectedDrive> activeDrives = driveRepo.findByMonitoredFolderIdsNotNullAndActiveTrue();

        if (activeDrives.isEmpty()) return;

        for (ConnectedDrive drive : activeDrives) {
            // Per-source lock: only one replica processes a given drive at a time
            // (Phase 1.13 — per-source watch sharding via ShedLock).
            String lockName = "drive-poll-" + drive.getId();
            perSourceLock.withLock(lockName, Duration.ofMinutes(10), () -> processDrive(drive));
        }
    }

    private void processDrive(ConnectedDrive drive) {
        boolean allSucceeded = true;
        for (String folderId : drive.getMonitoredFolderIds()) {
            try {
                checkFolder(drive, folderId);
            } catch (Exception e) {
                allSucceeded = false;
                log.error("Failed to check monitored folder {} for drive {}: {}",
                        folderId, drive.getProviderAccountEmail(), e.getMessage());
                try {
                    SystemError error = SystemError.of("ERROR", "EXTERNAL_API",
                            "Drive folder monitor failed for folder " + folderId + " on " + drive.getProviderAccountEmail() + ": " + e.getMessage());
                    error.setService("api");
                    systemErrorRepo.save(error);
                } catch (Exception ex) {
                    log.warn("Failed to persist folder monitor error: {}", ex.getMessage());
                }
            }
        }
        // Only update last sync time if all folders were checked successfully
        if (allSucceeded) {
            drive.setLastSyncAt(Instant.now());
            driveRepo.save(drive);
        }
    }

    private void checkFolder(ConnectedDrive drive, String folderId) throws Exception {
        List<DriveFileInfo> files = googleDriveService.listFilesInternal(drive, folderId);

        int registered = 0;
        for (DriveFileInfo file : files) {
            if (file.folder()) continue;

            // Check if already registered by fileId — indexed query, not full scan
            boolean exists = mongoTemplate.exists(
                    Query.query(Criteria.where("storageProvider").is("GOOGLE_DRIVE")
                            .and("externalStorageRef.fileId").is(file.id())),
                    DocumentModel.class);

            if (exists) continue;

            try {
                registerFile(drive, file);
                registered++;
            } catch (Exception e) {
                log.warn("Failed to register monitored file {}: {}", file.name(), e.getMessage());
            }
        }

        if (registered > 0) {
            log.info("Auto-registered {} new file(s) from monitored folder {} ({})",
                    registered, folderId, drive.getProviderAccountEmail());
        }
    }

    private void registerFile(ConnectedDrive drive, DriveFileInfo info) throws Exception {
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
                "providerAccountEmail", drive.getProviderAccountEmail(),
                "monitoredFolder", "true"
        ));
        doc.setStatus(DocumentStatus.UPLOADED);
        doc.setUploadedBy(drive.getUserId());
        doc.setCreatedAt(Instant.now());
        doc.setUpdatedAt(Instant.now());

        doc = documentService.save(doc);
        doc.setSlug(SlugGenerator.generate(info.name(), doc.getId()));
        doc = documentService.save(doc);

        // Storage mode: "cache" downloads to MinIO, "stream" processes directly from Drive
        String storageMode = appConfigService.getValue("drives.storage_mode", "cache");

        if ("cache".equals(storageMode)) {
            InputStream content = googleDriveService.downloadContent(drive, info.id(), info.mimeType());
            String storageKey = doc.getId() + "-" + sanitizeStorageKey(info.name());
            doc.setStorageBucket("gls-documents");
            doc.setStorageKey(storageKey);
            documentService.save(doc);

            byte[] bytes = content.readAllBytes();
            objectStorage.upload(storageKey, new java.io.ByteArrayInputStream(bytes), bytes.length, doc.getMimeType());
        } else {
            log.info("Stream mode — skipping MinIO cache for monitored Drive file: {}", info.name());
        }

        // Queue for processing (storageBucket/storageKey may be null in stream mode)
        try {
            rabbitTemplate.convertAndSend(EXCHANGE, ROUTING_INGESTED, new DocumentIngestedEvent(
                    doc.getId(), info.name(), doc.getMimeType(), info.size(),
                    doc.getStorageBucket(), doc.getStorageKey(), drive.getUserId(), Instant.now(),
                    null
            ));
        } catch (Exception e) {
            log.error("Failed to queue Drive file {} for processing: {}", info.name(), e.getMessage());
            documentService.setError(doc.getId(), DocumentStatus.PROCESSING_FAILED, "QUEUE", e.getMessage());
        }
    }

    private static String sanitizeStorageKey(String name) {
        if (name == null) return "unknown";
        return name.replaceAll("[/\\\\:*?\"<>|,]", "_").replaceAll("\\s+", " ").trim();
    }
}
