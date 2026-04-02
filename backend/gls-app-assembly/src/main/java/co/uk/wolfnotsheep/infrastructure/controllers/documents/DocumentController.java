package co.uk.wolfnotsheep.infrastructure.controllers.documents;

import java.util.Map;
import co.uk.wolfnotsheep.document.events.DocumentIngestedEvent;
import co.uk.wolfnotsheep.document.models.AuditEvent;
import co.uk.wolfnotsheep.document.models.DocumentModel;
import co.uk.wolfnotsheep.document.models.DocumentStatus;
import co.uk.wolfnotsheep.document.services.DocumentService;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.Instant;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private static final String EXCHANGE = "gls.documents";
    private static final String ROUTING_INGESTED = "document.ingested";

    private final DocumentService documentService;
    private final RabbitTemplate rabbitTemplate;

    public DocumentController(DocumentService documentService, RabbitTemplate rabbitTemplate) {
        this.documentService = documentService;
        this.rabbitTemplate = rabbitTemplate;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentModel> upload(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserDetails user) {

        DocumentModel doc = documentService.ingest(file, user.getUsername(), null);

        // Publish ingested event to kick off the processing pipeline
        rabbitTemplate.convertAndSend(EXCHANGE, ROUTING_INGESTED, new DocumentIngestedEvent(
                doc.getId(),
                doc.getOriginalFileName(),
                doc.getMimeType(),
                doc.getFileSizeBytes(),
                doc.getStorageBucket(),
                doc.getStorageKey(),
                user.getUsername(),
                Instant.now()
        ));

        return ResponseEntity.ok(doc);
    }

    @GetMapping("/{id}")
    public ResponseEntity<DocumentModel> getById(@PathVariable String id) {
        DocumentModel doc = documentService.getById(id);
        if (doc == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(doc);
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<InputStreamResource> download(@PathVariable String id) {
        DocumentModel doc = documentService.getById(id);
        if (doc == null) return ResponseEntity.notFound().build();

        InputStream stream = documentService.downloadFile(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + doc.getOriginalFileName() + "\"")
                .contentType(MediaType.parseMediaType(doc.getMimeType()))
                .contentLength(doc.getFileSizeBytes())
                .body(new InputStreamResource(stream));
    }

    @GetMapping("/{id}/audit")
    public ResponseEntity<Page<AuditEvent>> getAuditTrail(
            @PathVariable String id, Pageable pageable) {
        return ResponseEntity.ok(documentService.getAuditTrail(id, pageable));
    }

    @GetMapping
    public ResponseEntity<Page<DocumentModel>> list(
            @RequestParam(required = false) String status,
            @AuthenticationPrincipal UserDetails user,
            Pageable pageable) {

        if (status != null) {
            return ResponseEntity.ok(
                    documentService.getByStatus(DocumentStatus.valueOf(status), pageable));
        }
        return ResponseEntity.ok(documentService.getByUploader(user.getUsername(), pageable));
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Long>> stats() {
        return ResponseEntity.ok(Map.of(
                "uploaded", documentService.countByStatus(DocumentStatus.UPLOADED),
                "processing", documentService.countByStatus(DocumentStatus.PROCESSING),
                "classified", documentService.countByStatus(DocumentStatus.CLASSIFIED),
                "reviewRequired", documentService.countByStatus(DocumentStatus.REVIEW_REQUIRED),
                "governanceApplied", documentService.countByStatus(DocumentStatus.GOVERNANCE_APPLIED)
        ));
    }
}
