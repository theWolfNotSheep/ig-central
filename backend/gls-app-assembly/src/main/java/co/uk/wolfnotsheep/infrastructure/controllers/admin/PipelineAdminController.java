package co.uk.wolfnotsheep.infrastructure.controllers.admin;

import co.uk.wolfnotsheep.document.models.DocumentModel;
import co.uk.wolfnotsheep.document.services.DocumentService;
import co.uk.wolfnotsheep.document.services.PiiPatternScanner;
import co.uk.wolfnotsheep.document.models.PiiEntity;
import co.uk.wolfnotsheep.governance.models.PipelineDefinition;
import co.uk.wolfnotsheep.governance.repositories.PipelineDefinitionRepository;
import co.uk.wolfnotsheep.governance.services.PipelineRoutingService;
import co.uk.wolfnotsheep.governance.services.PipelineRoutingService.PipelineOverlap;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/pipelines")
public class PipelineAdminController {

    private final PipelineDefinitionRepository pipelineRepo;
    private final DocumentService documentService;
    private final PiiPatternScanner piiScanner;
    private final PipelineRoutingService routingService;

    public PipelineAdminController(PipelineDefinitionRepository pipelineRepo,
                                    DocumentService documentService,
                                    PiiPatternScanner piiScanner,
                                    PipelineRoutingService routingService) {
        this.pipelineRepo = pipelineRepo;
        this.documentService = documentService;
        this.piiScanner = piiScanner;
        this.routingService = routingService;
    }

    @GetMapping
    public ResponseEntity<List<PipelineDefinition>> list() {
        return ResponseEntity.ok(pipelineRepo.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<PipelineDefinition> get(@PathVariable String id) {
        return pipelineRepo.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<PipelineDefinition> create(@RequestBody PipelineDefinition pipeline) {
        pipeline.setCreatedAt(Instant.now());
        pipeline.setUpdatedAt(Instant.now());
        return ResponseEntity.ok(pipelineRepo.save(pipeline));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PipelineDefinition> update(
            @PathVariable String id, @RequestBody PipelineDefinition updates) {
        return pipelineRepo.findById(id)
                .map(existing -> {
                    existing.setName(updates.getName());
                    existing.setDescription(updates.getDescription());
                    existing.setActive(updates.isActive());
                    existing.setSteps(updates.getSteps());
                    existing.setVisualNodes(updates.getVisualNodes());
                    existing.setVisualEdges(updates.getVisualEdges());
                    existing.setApplicableCategoryIds(updates.getApplicableCategoryIds());
                    existing.setIncludeSubCategories(updates.isIncludeSubCategories());
                    existing.setApplicableMimeTypes(updates.getApplicableMimeTypes());
                    existing.setDefault(updates.isDefault());
                    existing.setUpdatedAt(Instant.now());
                    return ResponseEntity.ok(pipelineRepo.save(existing));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        return pipelineRepo.findById(id)
                .map(p -> { p.setActive(false); p.setUpdatedAt(Instant.now()); pipelineRepo.save(p); return ResponseEntity.ok().<Void>build(); })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Trigger a batch PII scan on all documents that haven't been scanned yet.
     */
    @PostMapping("/pii-scan/batch")
    public ResponseEntity<Map<String, Object>> batchPiiScan() {
        List<DocumentModel> docs = documentService.getAll();
        int scanned = 0;
        int piiFound = 0;

        for (DocumentModel doc : docs) {
            if (doc.getExtractedText() == null || doc.getExtractedText().isBlank()) continue;

            List<PiiEntity> findings = piiScanner.scan(doc.getExtractedText());
            doc.setPiiFindings(findings);
            doc.setPiiScannedAt(Instant.now());
            documentService.save(doc);

            scanned++;
            piiFound += findings.size();
        }

        return ResponseEntity.ok(Map.of(
                "scanned", scanned,
                "piiEntitiesFound", piiFound
        ));
    }

    /**
     * Trigger PII scan on a single document.
     */
    @PostMapping("/pii-scan/{documentId}")
    public ResponseEntity<Map<String, Object>> scanDocument(@PathVariable String documentId) {
        DocumentModel doc = documentService.getById(documentId);
        if (doc == null) return ResponseEntity.notFound().build();
        if (doc.getExtractedText() == null) return ResponseEntity.ok(Map.of("piiEntitiesFound", 0));

        List<PiiEntity> findings = piiScanner.scan(doc.getExtractedText());
        doc.setPiiFindings(findings);
        doc.setPiiScannedAt(Instant.now());
        documentService.save(doc);

        return ResponseEntity.ok(Map.of(
                "documentId", documentId,
                "piiEntitiesFound", findings.size(),
                "findings", findings
        ));
    }

    /**
     * Check for overlapping pipeline coverage across taxonomy categories.
     */
    @GetMapping("/overlap-check")
    public ResponseEntity<Map<String, List<PipelineOverlap>>> checkOverlaps() {
        return ResponseEntity.ok(routingService.checkOverlaps());
    }

    /**
     * Resolve which pipeline would handle a given category + mime type.
     */
    @GetMapping("/resolve")
    public ResponseEntity<Map<String, Object>> resolvePipeline(
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) String mimeType) {
        PipelineDefinition resolved = routingService.resolve(categoryId, mimeType);
        if (resolved == null) {
            return ResponseEntity.ok(Map.of("resolved", false));
        }
        return ResponseEntity.ok(Map.of(
                "resolved", true,
                "pipelineId", resolved.getId(),
                "pipelineName", resolved.getName(),
                "isDefault", resolved.isDefault()
        ));
    }
}
