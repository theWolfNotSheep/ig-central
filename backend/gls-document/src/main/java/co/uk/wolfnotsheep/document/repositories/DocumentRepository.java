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
}
