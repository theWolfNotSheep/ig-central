package co.uk.wolfnotsheep.infrastructure.controllers.admin;

import co.uk.wolfnotsheep.governance.models.InstalledPack;
import co.uk.wolfnotsheep.governance.models.PackUpdateAvailable;
import co.uk.wolfnotsheep.governance.repositories.InstalledPackRepository;
import co.uk.wolfnotsheep.governance.repositories.PackUpdateAvailableRepository;
import co.uk.wolfnotsheep.infrastructure.services.PackUpdateObserver;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Exposes installed governance packs and available updates to the frontend.
 */
@RestController
@RequestMapping("/api/admin/governance/updates")
public class PackUpdatesController {

    private final InstalledPackRepository installedRepo;
    private final PackUpdateAvailableRepository updateRepo;
    private final PackUpdateObserver observer;

    public PackUpdatesController(InstalledPackRepository installedRepo,
                                  PackUpdateAvailableRepository updateRepo,
                                  PackUpdateObserver observer) {
        this.installedRepo = installedRepo;
        this.updateRepo = updateRepo;
        this.observer = observer;
    }

    @GetMapping
    public ResponseEntity<List<PackUpdateAvailable>> getAvailableUpdates() {
        return ResponseEntity.ok(updateRepo.findByDismissedFalse());
    }

    @GetMapping("/count")
    public ResponseEntity<Map<String, Long>> getUpdateCount() {
        return ResponseEntity.ok(Map.of("count", updateRepo.countByDismissedFalse()));
    }

    @GetMapping("/installed")
    public ResponseEntity<List<InstalledPackStatus>> getInstalledPacks() {
        List<InstalledPack> installed = installedRepo.findAll();
        Map<String, PackUpdateAvailable> updatesBySlug = updateRepo.findAll().stream()
                .collect(Collectors.toMap(PackUpdateAvailable::getPackSlug, u -> u, (a, b) -> a));

        List<InstalledPackStatus> result = installed.stream().map(ip -> {
            PackUpdateAvailable update = updatesBySlug.get(ip.getPackSlug());
            return new InstalledPackStatus(
                    ip.getPackSlug(),
                    ip.getPackName(),
                    ip.getInstalledVersion(),
                    ip.getImportedAt(),
                    ip.getComponentTypesImported(),
                    update != null && !update.isDismissed(),
                    update != null ? update.getLatestVersion() : null,
                    update != null ? update.getChangelog() : null
            );
        }).toList();

        return ResponseEntity.ok(result);
    }

    @PostMapping("/{slug}/dismiss")
    public ResponseEntity<Void> dismissUpdate(@PathVariable String slug) {
        updateRepo.findByPackSlug(slug).ifPresent(update -> {
            update.setDismissed(true);
            updateRepo.save(update);
        });
        return ResponseEntity.ok().build();
    }

    @PostMapping("/check-now")
    public ResponseEntity<List<PackUpdateAvailable>> triggerCheck() {
        List<PackUpdateAvailable> updates = observer.checkNow();
        return ResponseEntity.ok(updates);
    }

    public record InstalledPackStatus(
            String packSlug,
            String packName,
            int installedVersion,
            Instant importedAt,
            List<String> componentTypes,
            boolean updateAvailable,
            Integer latestVersion,
            String changelog
    ) {}
}
