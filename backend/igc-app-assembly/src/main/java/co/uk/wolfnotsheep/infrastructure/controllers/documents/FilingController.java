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

    @GetMapping("/triage")
    public ResponseEntity<Page<DocumentModel>> triage(@AuthenticationPrincipal UserDetails user,
                                                      Pageable pageable) {
        Page<DocumentModel> docs = documentRepository.findByStatusAndUploadedByAndStorageProviderOrderByUpdatedAtDesc(
                DocumentStatus.TRIAGE, user.getUsername(), "LOCAL", pageable);
        return ResponseEntity.ok(docs);
    }

    @GetMapping("/triage/count")
    public ResponseEntity<Map<String, Long>> triageCount(@AuthenticationPrincipal UserDetails user) {
        long count = documentRepository.countByStatusAndUploadedByAndStorageProvider(
                DocumentStatus.TRIAGE, user.getUsername(), "LOCAL");
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

    @PostMapping("/{documentId}/return-to-triage")
    public ResponseEntity<DocumentModel> returnToTriage(@PathVariable String documentId,
                                                        @AuthenticationPrincipal UserDetails user) {
        DocumentModel doc = filingService.returnToTriage(documentId, user.getUsername());
        return ResponseEntity.ok(doc);
    }

    public record FileRequest(String driveId, String folderId) {}
}
