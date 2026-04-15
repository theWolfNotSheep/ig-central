package co.uk.wolfnotsheep.hub.app.services;

import co.uk.wolfnotsheep.hub.models.GovernancePack;
import co.uk.wolfnotsheep.hub.models.GovernancePack.PackStatus;
import co.uk.wolfnotsheep.hub.models.PackComponent;
import co.uk.wolfnotsheep.hub.models.PackVersion;
import co.uk.wolfnotsheep.hub.repositories.GovernancePackRepository;
import co.uk.wolfnotsheep.hub.repositories.PackVersionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class PackVersionService {

    private static final Logger log = LoggerFactory.getLogger(PackVersionService.class);

    private final PackVersionRepository versionRepository;
    private final GovernancePackRepository packRepository;

    public PackVersionService(PackVersionRepository versionRepository,
                              GovernancePackRepository packRepository) {
        this.versionRepository = versionRepository;
        this.packRepository = packRepository;
    }

    public PackVersion publishVersion(String packId, String changelog,
                                      List<PackComponent> components, String publishedBy) {
        GovernancePack pack = packRepository.findById(packId)
                .orElseThrow(() -> new IllegalArgumentException("Pack not found with id: " + packId));

        if (components == null || components.isEmpty()) {
            throw new IllegalArgumentException("At least one component is required for a version");
        }

        int nextVersion = pack.getLatestVersionNumber() + 1;

        PackVersion version = new PackVersion();
        version.setPackId(packId);
        version.setVersionNumber(nextVersion);
        version.setChangelog(changelog);
        version.setPublishedBy(publishedBy);
        version.setPublishedAt(Instant.now());
        version.setComponents(components);

        PackVersion saved = versionRepository.save(version);

        pack.setLatestVersionNumber(nextVersion);
        pack.setUpdatedAt(Instant.now());
        if (pack.getStatus() == PackStatus.DRAFT) {
            pack.setStatus(PackStatus.PUBLISHED);
            pack.setPublishedAt(Instant.now());
        }
        packRepository.save(pack);

        log.info("Published version {} for pack: {} (id: {})", nextVersion, pack.getName(), packId);
        return saved;
    }

    public PackVersion getVersion(String packId, int versionNumber) {
        return versionRepository.findByPackIdAndVersionNumber(packId, versionNumber)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Version " + versionNumber + " not found for pack: " + packId));
    }

    public List<PackVersion> getVersions(String packId) {
        if (!packRepository.existsById(packId)) {
            throw new IllegalArgumentException("Pack not found with id: " + packId);
        }
        return versionRepository.findByPackIdOrderByVersionNumberDesc(packId);
    }
}
