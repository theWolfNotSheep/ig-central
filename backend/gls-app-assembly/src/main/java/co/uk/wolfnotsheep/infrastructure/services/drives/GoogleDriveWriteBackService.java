package co.uk.wolfnotsheep.infrastructure.services.drives;

import co.uk.wolfnotsheep.document.models.ConnectedDrive;
import co.uk.wolfnotsheep.document.models.DocumentModel;
import co.uk.wolfnotsheep.document.models.SystemError;
import co.uk.wolfnotsheep.document.repositories.ConnectedDriveRepository;
import co.uk.wolfnotsheep.document.repositories.DocumentRepository;
import co.uk.wolfnotsheep.document.repositories.SystemErrorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;

/**
 * Writes classification results back to Google Drive as custom file properties.
 * Triggered automatically when a Google Drive document reaches a terminal status.
 */
@Service
public class GoogleDriveWriteBackService {

    private static final Logger log = LoggerFactory.getLogger(GoogleDriveWriteBackService.class);
    private static final Set<String> TERMINAL_STATUSES = Set.of(
            "GOVERNANCE_APPLIED", "REVIEW_REQUIRED", "CLASSIFIED", "ARCHIVED",
            "INBOX", "FILED"
    );

    private final DocumentRepository documentRepo;
    private final ConnectedDriveRepository driveRepo;
    private final GoogleDriveService googleDriveService;
    private final SystemErrorRepository systemErrorRepo;

    public GoogleDriveWriteBackService(DocumentRepository documentRepo,
                                        ConnectedDriveRepository driveRepo,
                                        GoogleDriveService googleDriveService,
                                        SystemErrorRepository systemErrorRepo) {
        this.documentRepo = documentRepo;
        this.driveRepo = driveRepo;
        this.googleDriveService = googleDriveService;
        this.systemErrorRepo = systemErrorRepo;
    }

    /**
     * Write classification back to Google Drive if this is a Drive document
     * that has reached a terminal status.
     */
    @Async
    public void writeBackIfNeeded(String documentId, String status) {
        if (!TERMINAL_STATUSES.contains(status)) return;

        try {
            DocumentModel doc = documentRepo.findById(documentId).orElse(null);
            if (doc == null || !"GOOGLE_DRIVE".equals(doc.getStorageProvider())) return;

            Map<String, String> ref = doc.getExternalStorageRef();
            if (ref == null || ref.get("driveId") == null || ref.get("fileId") == null) return;

            ConnectedDrive drive = driveRepo.findById(ref.get("driveId")).orElse(null);
            if (drive == null || !drive.isActive()) {
                log.debug("Drive not active for write-back on doc {}", documentId);
                return;
            }

            googleDriveService.writeClassificationProperties(drive, ref.get("fileId"),
                    doc.getStatus() != null ? doc.getStatus().name() : "",
                    doc.getCategoryName(),
                    doc.getSensitivityLabel() != null ? doc.getSensitivityLabel().name() : "",
                    doc.getId());

            log.info("Wrote classification back to Google Drive for doc {} ({})",
                    documentId, doc.getOriginalFileName());

        } catch (Exception e) {
            log.error("Google Drive write-back failed for doc {}: {}", documentId, e.getMessage());
            try {
                SystemError error = SystemError.of("ERROR", "EXTERNAL_API",
                        "Google Drive write-back failed for document " + documentId + ": " + e.getMessage());
                error.setDocumentId(documentId);
                error.setService("api");
                systemErrorRepo.save(error);
            } catch (Exception ex) {
                log.warn("Failed to persist Drive write-back error: {}", ex.getMessage());
            }
        }
    }
}
