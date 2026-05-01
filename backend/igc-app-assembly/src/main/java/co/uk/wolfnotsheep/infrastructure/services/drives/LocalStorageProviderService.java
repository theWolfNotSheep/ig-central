package co.uk.wolfnotsheep.infrastructure.services.drives;

import co.uk.wolfnotsheep.document.models.ConnectedDrive;
import co.uk.wolfnotsheep.document.models.DocumentModel;
import co.uk.wolfnotsheep.document.models.FolderModel;
import co.uk.wolfnotsheep.document.models.StorageProviderType;
import co.uk.wolfnotsheep.document.repositories.DocumentRepository;
import co.uk.wolfnotsheep.document.repositories.FolderRepository;
import co.uk.wolfnotsheep.document.services.ObjectStorageService;
import co.uk.wolfnotsheep.document.services.StorageProviderService;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Storage provider for local MinIO/S3-compatible object storage.
 * Wraps the existing ObjectStorageService and FolderModel to present
 * local storage as a "drive" in the unified browsing UI.
 */
@Service
public class LocalStorageProviderService implements StorageProviderService {

    private final ObjectStorageService objectStorage;
    private final FolderRepository folderRepo;
    private final DocumentRepository documentRepo;
    private final org.springframework.data.mongodb.core.MongoTemplate mongoTemplate;

    public LocalStorageProviderService(ObjectStorageService objectStorage,
                                       FolderRepository folderRepo,
                                       DocumentRepository documentRepo,
                                       org.springframework.data.mongodb.core.MongoTemplate mongoTemplate) {
        this.objectStorage = objectStorage;
        this.folderRepo = folderRepo;
        this.documentRepo = documentRepo;
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public StorageProviderType getType() {
        return StorageProviderType.LOCAL;
    }

    @Override
    public List<FileEntry> listFiles(ConnectedDrive drive, String folderId) {
        List<DocumentModel> docs;
        if (folderId != null) {
            docs = documentRepo.findByFolderIdOrderByOriginalFileNameAsc(folderId);
        } else {
            // Root level: local documents with no folder (storageProvider is LOCAL, MINIO, or null)
            var query = org.springframework.data.mongodb.core.query.Query.query(
                    new org.springframework.data.mongodb.core.query.Criteria().andOperator(
                            org.springframework.data.mongodb.core.query.Criteria.where("folderId").is(null),
                            new org.springframework.data.mongodb.core.query.Criteria().orOperator(
                                    org.springframework.data.mongodb.core.query.Criteria.where("storageProvider").in("LOCAL", "MINIO"),
                                    org.springframework.data.mongodb.core.query.Criteria.where("storageProvider").is(null)
                            )
                    )
            ).with(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt"));
            docs = new java.util.ArrayList<>(mongoTemplate.find(query, DocumentModel.class));
        }

        return docs.stream().map(d -> new FileEntry(
                d.getId(),
                d.getOriginalFileName() != null ? d.getOriginalFileName() : d.getFileName(),
                d.getMimeType(),
                d.getFileSizeBytes(),
                d.getUpdatedAt() != null ? d.getUpdatedAt() : d.getCreatedAt(),
                d.getUploadedBy(),
                null,
                false,
                Map.of("status", d.getStatus() != null ? d.getStatus().name() : "",
                       "categoryName", d.getCategoryName() != null ? d.getCategoryName() : "",
                       "sensitivityLabel", d.getSensitivityLabel() != null ? d.getSensitivityLabel().name() : "")
        )).toList();
    }

    @Override
    public List<FileEntry> listFolders(ConnectedDrive drive, String parentId) {
        List<FolderModel> folders;
        if (parentId != null) {
            folders = folderRepo.findByParentIdOrderByNameAsc(parentId);
        } else {
            // Root folders — show all (system drive is global)
            folders = folderRepo.findAll();
            folders = folders.stream().filter(f -> f.getParentId() == null).toList();
        }

        return folders.stream().map(f -> FileEntry.folder(f.getId(), f.getName())).toList();
    }

    @Override
    public InputStream downloadContent(ConnectedDrive drive, String fileId) throws Exception {
        DocumentModel doc = documentRepo.findById(fileId).orElse(null);
        if (doc == null || doc.getStorageKey() == null) {
            throw new IllegalArgumentException("Document not found or has no storage key: " + fileId);
        }
        return objectStorage.download(
                doc.getStorageBucket() != null ? doc.getStorageBucket() : "igc-documents",
                doc.getStorageKey());
    }

    @Override
    public FileEntry getFileInfo(ConnectedDrive drive, String fileId) {
        DocumentModel doc = documentRepo.findById(fileId).orElse(null);
        if (doc == null) return null;
        return new FileEntry(
                doc.getId(),
                doc.getOriginalFileName(),
                doc.getMimeType(),
                doc.getFileSizeBytes(),
                doc.getUpdatedAt(),
                doc.getUploadedBy(),
                null, false, Map.of());
    }

    @Override
    public void uploadFile(ConnectedDrive drive, String folderId, String fileName,
                           InputStream content, long size, String contentType) throws Exception {
        // Upload is handled by DocumentController / DocumentService.ingest()
        // This method is a pass-through — actual logic is in the controller
        throw new UnsupportedOperationException("Use DocumentService.ingest() for local uploads");
    }
}
