package co.uk.wolfnotsheep.document.repositories;

import co.uk.wolfnotsheep.document.models.DocumentModel;
import co.uk.wolfnotsheep.document.models.DocumentStatus;
import co.uk.wolfnotsheep.governance.models.SensitivityLabel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface DocumentRepository extends MongoRepository<DocumentModel, String> {

    Page<DocumentModel> findByUploadedBy(String uploadedBy, Pageable pageable);

    Page<DocumentModel> findByStatus(DocumentStatus status, Pageable pageable);

    Page<DocumentModel> findByOrganisationId(String organisationId, Pageable pageable);

    List<DocumentModel> findBySensitivityLabel(SensitivityLabel label);

    Optional<DocumentModel> findBySha256Hash(String hash);

    List<DocumentModel> findByRetentionExpiresAtBeforeAndLegalHoldFalseAndStatusNot(
            Instant now, DocumentStatus excluded);

    Page<DocumentModel> findByStatusIn(List<DocumentStatus> statuses, Pageable pageable);

    long countByStatus(DocumentStatus status);

    List<DocumentModel> findByFolderIdOrderByOriginalFileNameAsc(String folderId);

    List<DocumentModel> findByFolderIdIsNullAndUploadedByOrderByOriginalFileNameAsc(String uploadedBy);

    List<DocumentModel> findByCategoryIdAndUploadedByOrderByOriginalFileNameAsc(String categoryId, String uploadedBy);

    List<DocumentModel> findByCategoryNameAndUploadedByOrderByOriginalFileNameAsc(String categoryName, String uploadedBy);

    List<DocumentModel> findByCategoryIdIsNullAndUploadedByOrderByCreatedAtDesc(String uploadedBy);

    Optional<DocumentModel> findBySlug(String slug);

    long countByStatusIn(List<DocumentStatus> statuses);

    List<DocumentModel> findByFolderIdIsNullAndStorageProviderInOrderByCreatedAtDesc(List<String> providers);

    List<DocumentModel> findByConnectedDriveIdOrderByCreatedAtDesc(String connectedDriveId);

    long countByConnectedDriveId(String connectedDriveId);

    List<DocumentModel> findByStatusAndMimeType(DocumentStatus status, String mimeType);

    List<DocumentModel> findByStatusInAndMimeType(List<DocumentStatus> statuses, String mimeType);

    Page<DocumentModel> findByStatusAndUploadedByOrderByUpdatedAtDesc(DocumentStatus status, String uploadedBy, Pageable pageable);

    long countByStatusAndUploadedBy(DocumentStatus status, String uploadedBy);

    long countByStatusAndUpdatedAtBefore(DocumentStatus status, Instant before);

}
