package co.uk.wolfnotsheep.infrastructure.services.filing;

import co.uk.wolfnotsheep.document.models.ConnectedDrive;
import co.uk.wolfnotsheep.document.models.DocumentModel;
import co.uk.wolfnotsheep.document.models.DocumentStatus;
import co.uk.wolfnotsheep.document.models.StorageProviderType;
import co.uk.wolfnotsheep.document.repositories.AuditEventRepository;
import co.uk.wolfnotsheep.document.repositories.ConnectedDriveRepository;
import co.uk.wolfnotsheep.document.models.AuditEvent;
import co.uk.wolfnotsheep.document.services.DocumentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

@Service
public class FilingService {

    private static final Logger log = LoggerFactory.getLogger(FilingService.class);

    private final DocumentService documentService;
    private final ConnectedDriveRepository driveRepository;
    private final AuditEventRepository auditEventRepository;

    public FilingService(DocumentService documentService,
                         ConnectedDriveRepository driveRepository,
                         AuditEventRepository auditEventRepository) {
        this.documentService = documentService;
        this.driveRepository = driveRepository;
        this.auditEventRepository = auditEventRepository;
    }

    public DocumentModel fileDocument(String documentId, String driveId, String folderId, String performedBy) {
        DocumentModel doc = documentService.getById(documentId);
        if (doc == null) {
            throw new IllegalArgumentException("Document not found: " + documentId);
        }
        if (doc.getStatus() != DocumentStatus.INBOX) {
            throw new IllegalArgumentException("Document is not in INBOX status (current: " + doc.getStatus() + ")");
        }

        ConnectedDrive drive = driveRepository.findById(driveId)
                .orElseThrow(() -> new IllegalArgumentException("Connected drive not found: " + driveId));

        StorageProviderType providerType = drive.getProviderType();
        if (providerType == null) {
            providerType = StorageProviderType.LOCAL;
        }

        switch (providerType) {
            case LOCAL -> {
                // Local filing: just set the folder, no file movement needed
                doc.setFolderId(folderId);
                doc.setConnectedDriveId(driveId);
            }
            default -> throw new UnsupportedOperationException(
                    "Filing to " + providerType + " is not yet supported. Coming soon.");
        }

        doc.setFiledToDriveId(driveId);
        doc.setFiledToFolderId(folderId);
        doc.setFiledAt(Instant.now());
        doc.setFiledBy(performedBy);
        doc.setStatus(DocumentStatus.FILED);
        doc.setUpdatedAt(Instant.now());

        DocumentModel saved = documentService.save(doc);

        auditEventRepository.save(new AuditEvent(
                documentId, "DOCUMENT_FILED", performedBy, "USER",
                Map.of("driveId", driveId,
                        "folderId", folderId != null ? folderId : "root",
                        "provider", providerType.name())));

        log.info("Document {} filed to drive {} folder {} by {}", documentId, driveId, folderId, performedBy);
        return saved;
    }

    public DocumentModel returnToTriage(String documentId, String performedBy) {
        DocumentModel doc = documentService.getById(documentId);
        if (doc == null) {
            throw new IllegalArgumentException("Document not found: " + documentId);
        }
        if (doc.getStatus() != DocumentStatus.INBOX) {
            throw new IllegalArgumentException("Document is not in INBOX status (current: " + doc.getStatus() + ")");
        }

        doc.setStatus(DocumentStatus.TRIAGE);
        doc.setUpdatedAt(Instant.now());

        DocumentModel saved = documentService.save(doc);

        auditEventRepository.save(new AuditEvent(
                documentId, "DOCUMENT_RETURNED_TO_TRIAGE", performedBy, "USER",
                Map.of()));

        log.info("Document {} returned to triage by {}", documentId, performedBy);
        return saved;
    }

    public DocumentModel returnToInbox(String documentId, String performedBy) {
        DocumentModel doc = documentService.getById(documentId);
        if (doc == null) {
            throw new IllegalArgumentException("Document not found: " + documentId);
        }
        if (doc.getStatus() != DocumentStatus.FILED) {
            throw new IllegalArgumentException("Document is not in FILED status (current: " + doc.getStatus() + ")");
        }

        doc.setFiledToDriveId(null);
        doc.setFiledToFolderId(null);
        doc.setFiledAt(null);
        doc.setFiledBy(null);
        doc.setFolderId(null);
        doc.setStatus(DocumentStatus.INBOX);
        doc.setUpdatedAt(Instant.now());

        DocumentModel saved = documentService.save(doc);

        auditEventRepository.save(new AuditEvent(
                documentId, "DOCUMENT_RETURNED_TO_INBOX", performedBy, "USER",
                Map.of()));

        log.info("Document {} returned to inbox by {}", documentId, performedBy);
        return saved;
    }
}
