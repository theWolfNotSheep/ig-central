package co.uk.wolfnotsheep.hub.app.controllers;

import co.uk.wolfnotsheep.hub.models.GovernancePack;
import co.uk.wolfnotsheep.hub.models.PackComponent;
import co.uk.wolfnotsheep.hub.models.PackVersion;
import co.uk.wolfnotsheep.hub.app.services.GovernancePackService;
import co.uk.wolfnotsheep.hub.app.services.PackVersionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/hub/admin/packs")
public class PackAdminController {

    private static final Logger log = LoggerFactory.getLogger(PackAdminController.class);

    private final GovernancePackService packService;
    private final PackVersionService versionService;

    public PackAdminController(GovernancePackService packService, PackVersionService versionService) {
        this.packService = packService;
        this.versionService = versionService;
    }

    @PostMapping
    public ResponseEntity<GovernancePack> createPack(@RequestBody GovernancePack pack) {
        try {
            GovernancePack created = packService.createPack(pack);
            log.info("Admin created pack: {}", created.getName());
            return ResponseEntity.ok(created);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<GovernancePack> updatePack(@PathVariable String id,
                                                     @RequestBody GovernancePack updates) {
        try {
            GovernancePack updated = packService.updatePack(id, updates);
            log.info("Admin updated pack: {} (id: {})", updated.getName(), id);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/{id}/versions")
    public ResponseEntity<PackVersion> publishVersion(@PathVariable String id,
                                                      @RequestBody PublishVersionRequest request) {
        try {
            PackVersion version = versionService.publishVersion(
                    id, request.changelog(), request.components(), request.publishedBy());
            log.info("Admin published version {} for pack id: {}", version.getVersionNumber(), id);
            return ResponseEntity.ok(version);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<GovernancePack> deprecatePack(@PathVariable String id) {
        try {
            GovernancePack deprecated = packService.deprecatePack(id);
            log.info("Admin deprecated pack: {} (id: {})", deprecated.getName(), id);
            return ResponseEntity.ok(deprecated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    public record PublishVersionRequest(String changelog, List<PackComponent> components, String publishedBy) {}
}
