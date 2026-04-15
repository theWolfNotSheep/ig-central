package co.uk.wolfnotsheep.infrastructure.controllers.documents;

import co.uk.wolfnotsheep.document.models.DocumentModel;
import co.uk.wolfnotsheep.document.models.DocumentStatus;
import co.uk.wolfnotsheep.document.repositories.DocumentRepository;
import co.uk.wolfnotsheep.infrastructure.services.filing.FilingService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/filing")
public class FilingController {

    private final FilingService filingService;
    private final DocumentRepository documentRepository;

    public FilingController(FilingService filingService, DocumentRepository documentRepository) {
        this.filingService = filingService;
        this.documentRepository = documentRepository;
    }

    @GetMapping("/inbox")
    public ResponseEntity<Page<DocumentModel>> inbox(@AuthenticationPrincipal UserDetails user,
                                                      Pageable pageable) {
        Page<DocumentModel> docs = documentRepository.findByStatusAndUploadedByOrderByUpdatedAtDesc(
                DocumentStatus.INBOX, user.getUsername(), pageable);
        return ResponseEntity.ok(docs);
    }

    @GetMapping("/inbox/count")
    public ResponseEntity<Map<String, Long>> inboxCount(@AuthenticationPrincipal UserDetails user) {
        long count = documentRepository.countByStatusAndUploadedBy(DocumentStatus.INBOX, user.getUsername());
        return ResponseEntity.ok(Map.of("count", count));
    }

    @PostMapping("/{documentId}")
    public ResponseEntity<DocumentModel> fileDocument(@PathVariable String documentId,
                                                       @RequestBody FileRequest request,
                                                       @AuthenticationPrincipal UserDetails user) {
        DocumentModel doc = filingService.fileDocument(
                documentId, request.driveId(), request.folderId(), user.getUsername());
        return ResponseEntity.ok(doc);
    }

    @PostMapping("/{documentId}/return-to-inbox")
    public ResponseEntity<DocumentModel> returnToInbox(@PathVariable String documentId,
                                                        @AuthenticationPrincipal UserDetails user) {
        DocumentModel doc = filingService.returnToInbox(documentId, user.getUsername());
        return ResponseEntity.ok(doc);
    }

    public record FileRequest(String driveId, String folderId) {}
}
