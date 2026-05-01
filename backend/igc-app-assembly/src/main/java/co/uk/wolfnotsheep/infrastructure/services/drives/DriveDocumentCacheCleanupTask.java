package co.uk.wolfnotsheep.infrastructure.services.drives;

import co.uk.wolfnotsheep.document.models.DocumentModel;
import co.uk.wolfnotsheep.document.services.DocumentService;
import co.uk.wolfnotsheep.document.services.ObjectStorageService;
import co.uk.wolfnotsheep.platform.config.services.AppConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Scheduled cleanup of MinIO-cached files for Google Drive documents.
 * After classification is complete, the cached copy is no longer needed —
 * the viewer proxies directly from Google Drive API.
 *
 * Gated by config: drives.cache_cleanup_enabled (default false).
 */
@Service
public class DriveDocumentCacheCleanupTask {

    private static final Logger log = LoggerFactory.getLogger(DriveDocumentCacheCleanupTask.class);

    private final MongoTemplate mongoTemplate;
    private final DocumentService documentService;
    private final ObjectStorageService objectStorage;
    private final AppConfigService configService;

    public DriveDocumentCacheCleanupTask(MongoTemplate mongoTemplate,
                                          DocumentService documentService,
                                          ObjectStorageService objectStorage,
                                          AppConfigService configService) {
        this.mongoTemplate = mongoTemplate;
        this.documentService = documentService;
        this.objectStorage = objectStorage;
        this.configService = configService;
    }

    @Scheduled(fixedDelay = 1800000, initialDelay = 120000) // 30 min, 2 min initial delay
    public void cleanupCachedDriveFiles() {
        boolean enabled = configService.getValue("drives.cache_cleanup_enabled", false);
        if (!enabled) return;

        int delayHours = configService.getValue("drives.cache_cleanup_delay_hours", 1);
        Instant cutoff = Instant.now().minus(delayHours, ChronoUnit.HOURS);

        Query query = Query.query(Criteria.where("storageProvider").is("GOOGLE_DRIVE")
                .and("storageKey").ne(null)
                .and("status").in("CLASSIFIED", "GOVERNANCE_APPLIED", "FILED", "INBOX")
                .and("updatedAt").lt(cutoff));

        List<DocumentModel> docs = mongoTemplate.find(query, DocumentModel.class);
        if (docs.isEmpty()) return;

        int cleaned = 0;
        for (DocumentModel doc : docs) {
            if (doc.getStorageKey() == null) continue;

            try {
                objectStorage.delete(doc.getStorageBucket(), doc.getStorageKey());
                doc.setStorageKey(null);
                doc.setStorageBucket(null);
                documentService.save(doc);
                cleaned++;
            } catch (Exception e) {
                log.warn("Failed to cleanup cached file for doc {}: {}", doc.getId(), e.getMessage());
            }
        }

        if (cleaned > 0) {
            log.info("Cleaned up {} cached MinIO files for classified Google Drive documents", cleaned);
        }
    }
}
