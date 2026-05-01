package co.uk.wolfnotsheep.infrastructure.controllers.documents;

import co.uk.wolfnotsheep.document.models.DocumentModel;
import co.uk.wolfnotsheep.document.models.FolderModel;
import co.uk.wolfnotsheep.document.repositories.DocumentRepository;
import co.uk.wolfnotsheep.document.repositories.FolderRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/folders")
public class FolderController {

    private final FolderRepository folderRepo;
    private final DocumentRepository documentRepo;

    public FolderController(FolderRepository folderRepo, DocumentRepository documentRepo) {
        this.folderRepo = folderRepo;
        this.documentRepo = documentRepo;
    }

    /**
     * Get root folders for the current user.
     */
    @GetMapping
    public ResponseEntity<List<FolderModel>> roots(@AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(
                folderRepo.findByParentIdIsNullAndCreatedByOrderByNameAsc(user.getUsername()));
    }

    /**
     * Get children of a folder: sub-folders + documents.
     * Returns a map with { folders: [...], documents: [...] }
     */
    @GetMapping("/{id}/children")
    public ResponseEntity<Map<String, Object>> children(@PathVariable String id) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("folders", folderRepo.findByParentIdOrderByNameAsc(id));
        result.put("documents", documentRepo.findByFolderIdOrderByOriginalFileNameAsc(id));
        return ResponseEntity.ok(result);
    }

    /**
     * Get documents not in any folder (root-level documents).
     */
    @GetMapping("/root-documents")
    public ResponseEntity<List<DocumentModel>> rootDocuments(@AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(
                documentRepo.findByFolderIdIsNullAndUploadedByOrderByOriginalFileNameAsc(user.getUsername()));
    }

    /**
     * Create a new folder.
     */
    @PostMapping
    public ResponseEntity<FolderModel> create(
            @RequestBody FolderModel folder,
            @AuthenticationPrincipal UserDetails user) {
        folder.setCreatedBy(user.getUsername());
        folder.setCreatedAt(Instant.now());
        folder.setUpdatedAt(Instant.now());
        return ResponseEntity.ok(folderRepo.save(folder));
    }

    /**
     * Rename or move a folder.
     */
    @PutMapping("/{id}")
    public ResponseEntity<FolderModel> update(
            @PathVariable String id,
            @RequestBody FolderModel updates) {
        FolderModel folder = folderRepo.findById(id).orElse(null);
        if (folder == null) return ResponseEntity.notFound().build();

        if (updates.getName() != null) folder.setName(updates.getName());
        if (updates.getDescription() != null) folder.setDescription(updates.getDescription());
        if (updates.getParentId() != null) folder.setParentId(updates.getParentId());
        folder.setUpdatedAt(Instant.now());

        return ResponseEntity.ok(folderRepo.save(folder));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        folderRepo.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
