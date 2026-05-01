package co.uk.wolfnotsheep.infrastructure.controllers.admin;

import co.uk.wolfnotsheep.infrastructure.services.GovernanceHubClient;
import co.uk.wolfnotsheep.infrastructure.services.HubPackDto.PackVersionDto;
import co.uk.wolfnotsheep.infrastructure.services.PackDiffService;
import co.uk.wolfnotsheep.infrastructure.services.PackDiffService.PackDiffResult;
import co.uk.wolfnotsheep.infrastructure.services.PackImportService;
import co.uk.wolfnotsheep.infrastructure.services.PackImportService.ImportMode;
import co.uk.wolfnotsheep.infrastructure.services.PackImportService.ImportResult;
import co.uk.wolfnotsheep.infrastructure.services.PackImportService.SelectedItem;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Handles importing governance packs from the Hub into the local tenant database.
 */
@RestController
@RequestMapping("/api/admin/governance/import")
public class GovernanceImportController {

    private static final Logger log = LoggerFactory.getLogger(GovernanceImportController.class);

    private final GovernanceHubClient hubClient;
    private final PackImportService importService;
    private final PackDiffService diffService;
    private final ObjectMapper objectMapper;
    private final co.uk.wolfnotsheep.governance.repositories.PackImportHistoryRepository historyRepo;

    public GovernanceImportController(GovernanceHubClient hubClient,
                                      PackImportService importService,
                                      PackDiffService diffService,
                                      ObjectMapper objectMapper,
                                      co.uk.wolfnotsheep.governance.repositories.PackImportHistoryRepository historyRepo) {
        this.hubClient = hubClient;
        this.importService = importService;
        this.diffService = diffService;
        this.objectMapper = objectMapper;
        this.historyRepo = historyRepo;
    }

    public record ImportRequest(
            String packSlug,
            int versionNumber,
            List<String> componentTypes,
            String mode // MERGE, OVERWRITE, PREVIEW
    ) {}

    /**
     * Preview what an import would do without committing changes.
     */
    @PostMapping("/preview")
    public ResponseEntity<?> preview(@RequestBody ImportRequest request) {
        try {
            PackVersionDto version = downloadAndFilter(request);
            ImportResult result = importService.importPack(version, request.packSlug(), ImportMode.PREVIEW);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Import preview failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Import governance pack components into the local database.
     */
    @PostMapping
    public ResponseEntity<?> importPack(@RequestBody ImportRequest request,
                                        org.springframework.security.core.Authentication auth) {
        try {
            ImportMode mode = parseMode(request.mode());
            if (mode == ImportMode.PREVIEW) {
                return preview(request);
            }

            PackVersionDto version = downloadAndFilter(request);
            String importedBy = auth != null ? auth.getName() : null;
            ImportResult result = importService.importPack(version, request.packSlug(), mode, importedBy, null);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Import failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Compute a field-level diff between local data and a new hub version.
     */
    @PostMapping("/diff")
    public ResponseEntity<?> diff(@RequestBody ImportRequest request) {
        try {
            PackVersionDto version = downloadAndFilter(request);
            PackDiffResult result = diffService.computeDiff(version, request.packSlug());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Diff failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Import only selected items from a hub version (per-item accept/reject).
     */
    @PostMapping("/selective")
    public ResponseEntity<?> selectiveImport(@RequestBody SelectiveImportRequest request,
                                             org.springframework.security.core.Authentication auth) {
        try {
            PackVersionDto version = downloadAndFilter(
                    new ImportRequest(request.packSlug(), request.versionNumber(), request.componentTypes(), null));
            List<SelectedItem> selections = request.selectedItems().stream()
                    .map(s -> new SelectedItem(s.componentType(), s.itemKey()))
                    .toList();
            String importedBy = auth != null ? auth.getName() : null;
            ImportResult result = importService.importSelectedItems(
                    version, request.packSlug(), selections, importedBy);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Selective import failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * List the chronological import history for a given pack — every
     * non-PREVIEW import that's landed for this {@code packSlug}, newest
     * first. Operators use this to audit ("when did we last refresh the
     * UK-HR pack?") and to roll back ("re-import v3 to revert v4").
     *
     * <p>Re-import is just calling the existing import endpoints with an
     * older version number — no separate "rollback" endpoint is needed.
     */
    @GetMapping("/history/{packSlug}")
    public ResponseEntity<List<co.uk.wolfnotsheep.governance.models.PackImportHistory>> getHistory(
            @org.springframework.web.bind.annotation.PathVariable String packSlug) {
        return ResponseEntity.ok(historyRepo.findByPackSlugOrderByImportedAtDesc(packSlug));
    }

    public record SelectiveImportRequest(
            String packSlug,
            int versionNumber,
            List<String> componentTypes,
            List<SelectiveItem> selectedItems
    ) {}

    public record SelectiveItem(String componentType, String itemKey) {}

    private PackVersionDto downloadAndFilter(ImportRequest request) throws Exception {
        if (!hubClient.isConfigured()) {
            throw new IllegalStateException("Governance Hub is not configured. Set the Hub URL and API key in Settings.");
        }

        // Build request body for the hub download endpoint
        String downloadBody = null;
        if (request.componentTypes() != null && !request.componentTypes().isEmpty()) {
            downloadBody = objectMapper.writeValueAsString(Map.of("componentTypes", request.componentTypes()));
        }

        String json = hubClient.post(
                "/api/hub/packs/" + request.packSlug() + "/versions/" + request.versionNumber() + "/download",
                downloadBody);

        return objectMapper.readValue(json, PackVersionDto.class);
    }

    private static ImportMode parseMode(String mode) {
        if (mode == null || mode.isBlank()) return ImportMode.MERGE;
        try {
            return ImportMode.valueOf(mode.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ImportMode.MERGE;
        }
    }
}
