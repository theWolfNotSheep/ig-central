package co.uk.wolfnotsheep.infrastructure.bootstrap;

import co.uk.wolfnotsheep.document.models.ConnectedDrive;
import co.uk.wolfnotsheep.document.models.StorageProviderType;
import co.uk.wolfnotsheep.document.repositories.ConnectedDriveRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Ensures a "Local Storage" system drive exists on startup.
 * Also backfills existing documents that have no connectedDriveId.
 */
@Component
@Order(5) // Run after GovernanceDataSeeder
public class LocalDriveBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LocalDriveBootstrap.class);

    private final ConnectedDriveRepository driveRepo;
    private final MongoTemplate mongoTemplate;

    public LocalDriveBootstrap(ConnectedDriveRepository driveRepo, MongoTemplate mongoTemplate) {
        this.driveRepo = driveRepo;
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        ConnectedDrive localDrive = ensureLocalDrive();
        backfillDocuments(localDrive.getId());
    }

    private ConnectedDrive ensureLocalDrive() {
        return driveRepo.findBySystemDriveTrue().orElseGet(() -> {
            ConnectedDrive drive = new ConnectedDrive();
            drive.setProvider("LOCAL");
            drive.setProviderType(StorageProviderType.LOCAL);
            drive.setDisplayName("Local Storage");
            drive.setSystemDrive(true);
            drive.setActive(true);
            drive.setUserId(null); // System-wide
            drive.setConnectedAt(Instant.now());
            drive.setConfig(Map.of("bucket", "igc-documents"));
            drive.setMonitoredFolderIds(List.of());

            ConnectedDrive saved = driveRepo.save(drive);
            log.info("Created system Local Storage drive: {}", saved.getId());
            return saved;
        });
    }

    private void backfillDocuments(String localDriveId) {
        // Backfill documents with no connectedDriveId and local/null storage provider
        Query query = Query.query(Criteria.where("connectedDriveId").is(null)
                .and("storageProvider").in(null, "MINIO", "LOCAL", ""));
        Update update = new Update()
                .set("connectedDriveId", localDriveId)
                .set("storageProvider", "LOCAL");

        var result = mongoTemplate.updateMulti(query, update,
                co.uk.wolfnotsheep.document.models.DocumentModel.class);

        if (result.getModifiedCount() > 0) {
            log.info("Backfilled {} documents with Local Storage drive ID", result.getModifiedCount());
        }

        // Also backfill legacy drives with providerType
        Query legacyDrives = Query.query(Criteria.where("providerType").is(null)
                .and("provider").is("GOOGLE_DRIVE"));
        Update driveUpdate = new Update().set("providerType", StorageProviderType.GOOGLE_DRIVE.name());
        var driveResult = mongoTemplate.updateMulti(legacyDrives, driveUpdate, ConnectedDrive.class);
        if (driveResult.getModifiedCount() > 0) {
            log.info("Backfilled {} Google Drive connections with providerType", driveResult.getModifiedCount());
        }
    }
}
