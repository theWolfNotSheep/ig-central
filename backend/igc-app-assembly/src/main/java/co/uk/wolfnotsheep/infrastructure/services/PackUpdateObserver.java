package co.uk.wolfnotsheep.infrastructure.services;

import co.uk.wolfnotsheep.governance.models.InstalledPack;
import co.uk.wolfnotsheep.governance.models.PackUpdateAvailable;
import co.uk.wolfnotsheep.governance.repositories.InstalledPackRepository;
import co.uk.wolfnotsheep.governance.repositories.PackUpdateAvailableRepository;
import tools.jackson.databind.json.JsonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Periodically polls the Governance Hub for newer versions of imported packs.
 * Stores available updates in MongoDB and pushes notifications via SSE.
 */
@Component
public class PackUpdateObserver {

    private static final Logger log = LoggerFactory.getLogger(PackUpdateObserver.class);

    private final GovernanceHubClient hubClient;
    private final InstalledPackRepository installedPackRepo;
    private final PackUpdateAvailableRepository updateRepo;
    private final PipelineEventBroadcaster broadcaster;
    private final JsonMapper jsonMapper;

    public PackUpdateObserver(GovernanceHubClient hubClient,
                              InstalledPackRepository installedPackRepo,
                              PackUpdateAvailableRepository updateRepo,
                              PipelineEventBroadcaster broadcaster,
                              JsonMapper jsonMapper) {
        this.hubClient = hubClient;
        this.installedPackRepo = installedPackRepo;
        this.updateRepo = updateRepo;
        this.broadcaster = broadcaster;
        this.jsonMapper = jsonMapper;
    }

    @Scheduled(fixedDelayString = "${governance-hub.poll-interval-ms:3600000}",
               initialDelay = 60_000)
    public void checkForUpdates() {
        try {
            doCheck();
        } catch (Exception e) {
            log.warn("Hub update check failed (will retry next cycle): {}", e.getMessage());
        }
    }

    /**
     * Manually triggered check — returns the current list of available updates.
     */
    public List<PackUpdateAvailable> checkNow() {
        try {
            doCheck();
        } catch (Exception e) {
            log.warn("Manual hub update check failed: {}", e.getMessage());
        }
        return updateRepo.findByDismissedFalse();
    }

    private void doCheck() {
        if (!hubClient.isConfigured()) {
            return;
        }

        List<InstalledPack> installed = installedPackRepo.findAll();
        if (installed.isEmpty()) {
            return;
        }

        // Build batch request
        List<Map<String, Object>> requestBody = installed.stream()
                .map(p -> {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("slug", p.getPackSlug());
                    entry.put("currentVersion", p.getInstalledVersion());
                    return entry;
                })
                .toList();

        String responseJson;
        try {
            String body = jsonMapper.writeValueAsString(requestBody);
            responseJson = hubClient.post("/api/hub/packs/check-updates", body);
        } catch (Exception e) {
            throw new RuntimeException("Failed to call hub check-updates: " + e.getMessage(), e);
        }

        List<HubUpdateInfo> hubUpdates;
        try {
            hubUpdates = List.of(jsonMapper.readValue(responseJson, HubUpdateInfo[].class));
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse hub check-updates response: " + e.getMessage(), e);
        }

        // Index installed packs by slug for lookup
        Map<String, InstalledPack> installedBySlug = installed.stream()
                .collect(Collectors.toMap(InstalledPack::getPackSlug, p -> p));

        Set<String> updatedSlugs = new HashSet<>();
        int newUpdateCount = 0;

        for (HubUpdateInfo info : hubUpdates) {
            updatedSlugs.add(info.slug);
            InstalledPack ip = installedBySlug.get(info.slug);
            if (ip == null) continue;

            Optional<PackUpdateAvailable> existing = updateRepo.findByPackSlug(info.slug);
            PackUpdateAvailable update = existing.orElseGet(PackUpdateAvailable::new);

            boolean isNew = existing.isEmpty() || update.getLatestVersion() != info.latestVersion;

            update.setPackSlug(info.slug);
            update.setPackName(info.name);
            update.setInstalledVersion(ip.getInstalledVersion());
            update.setLatestVersion(info.latestVersion);
            update.setChangelog(info.changelog);
            update.setPublishedAt(info.publishedAt);
            update.setComponentTypes(info.componentTypes != null ? info.componentTypes : List.of());
            update.setDetectedAt(Instant.now());

            // Reset dismissed if a new version appeared
            if (isNew) {
                update.setDismissed(false);
                newUpdateCount++;
            }

            updateRepo.save(update);
        }

        // Remove stale updates for packs that no longer have a newer version
        Set<String> installedSlugs = installedBySlug.keySet();
        List<PackUpdateAvailable> allUpdates = updateRepo.findAll();
        for (PackUpdateAvailable existing : allUpdates) {
            if (installedSlugs.contains(existing.getPackSlug())
                    && !updatedSlugs.contains(existing.getPackSlug())) {
                updateRepo.deleteByPackSlug(existing.getPackSlug());
            }
        }

        if (newUpdateCount > 0) {
            log.info("Detected {} new pack update(s) from hub", newUpdateCount);
            long totalActive = updateRepo.countByDismissedFalse();
            broadcaster.broadcast("pack-updates", Map.of(
                    "count", totalActive,
                    "newUpdates", newUpdateCount
            ));
        } else {
            log.debug("Hub update check complete — no new updates");
        }
    }

    /**
     * Mirrors the hub's PackUpdateInfo response shape for deserialization.
     */
    private record HubUpdateInfo(
            String slug,
            String name,
            int latestVersion,
            String changelog,
            Instant publishedAt,
            List<String> componentTypes
    ) {}
}
