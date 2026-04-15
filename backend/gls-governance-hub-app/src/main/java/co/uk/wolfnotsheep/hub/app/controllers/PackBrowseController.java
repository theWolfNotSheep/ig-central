package co.uk.wolfnotsheep.hub.app.controllers;

import co.uk.wolfnotsheep.hub.models.GovernancePack;
import co.uk.wolfnotsheep.hub.models.HubApiKey;
import co.uk.wolfnotsheep.hub.models.PackComponent;
import co.uk.wolfnotsheep.hub.models.PackDownloadRecord;
import co.uk.wolfnotsheep.hub.models.PackReview;
import co.uk.wolfnotsheep.hub.models.PackVersion;
import co.uk.wolfnotsheep.hub.app.services.GovernancePackService;
import co.uk.wolfnotsheep.hub.app.services.PackVersionService;
import co.uk.wolfnotsheep.hub.repositories.GovernancePackRepository;
import co.uk.wolfnotsheep.hub.repositories.PackDownloadRecordRepository;
import co.uk.wolfnotsheep.hub.repositories.PackReviewRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/hub/packs")
public class PackBrowseController {

    private static final Logger log = LoggerFactory.getLogger(PackBrowseController.class);

    private final GovernancePackService packService;
    private final PackVersionService versionService;
    private final GovernancePackRepository packRepository;
    private final PackReviewRepository reviewRepository;
    private final PackDownloadRecordRepository downloadRecordRepository;

    public PackBrowseController(GovernancePackService packService,
                                PackVersionService versionService,
                                GovernancePackRepository packRepository,
                                PackReviewRepository reviewRepository,
                                PackDownloadRecordRepository downloadRecordRepository) {
        this.packService = packService;
        this.versionService = versionService;
        this.packRepository = packRepository;
        this.reviewRepository = reviewRepository;
        this.downloadRecordRepository = downloadRecordRepository;
    }

    @GetMapping
    public ResponseEntity<Page<GovernancePack>> searchPacks(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String jurisdiction,
            @RequestParam(required = false) String industry,
            @RequestParam(required = false) String regulation,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) Boolean featured,
            @PageableDefault(size = 20, sort = "downloadCount", direction = Sort.Direction.DESC) Pageable pageable) {

        Page<GovernancePack> results = packService.search(query, jurisdiction, industry,
                regulation, tag, featured, pageable);
        return ResponseEntity.ok(results);
    }

    @GetMapping("/{slug}")
    public ResponseEntity<GovernancePack> getPackDetail(@PathVariable String slug) {
        GovernancePack pack = packService.getPackDetail(slug);
        return ResponseEntity.ok(pack);
    }

    @GetMapping("/{slug}/versions")
    public ResponseEntity<List<PackVersion>> getVersions(@PathVariable String slug) {
        GovernancePack pack = packService.getPackDetail(slug);
        List<PackVersion> versions = versionService.getVersions(pack.getId());
        return ResponseEntity.ok(versions);
    }

    @GetMapping("/{slug}/versions/{versionNumber}")
    public ResponseEntity<PackVersion> getVersion(@PathVariable String slug,
                                                  @PathVariable int versionNumber) {
        GovernancePack pack = packService.getPackDetail(slug);
        PackVersion version = versionService.getVersion(pack.getId(), versionNumber);
        return ResponseEntity.ok(version);
    }

    @PostMapping("/{slug}/versions/{versionNumber}/download")
    public ResponseEntity<PackVersion> downloadVersion(
            @PathVariable String slug,
            @PathVariable int versionNumber,
            @RequestBody(required = false) DownloadRequest downloadRequest,
            Authentication authentication) {

        GovernancePack pack = packService.getPackDetail(slug);
        PackVersion version = versionService.getVersion(pack.getId(), versionNumber);

        // Filter components if requested
        if (downloadRequest != null && downloadRequest.componentTypes() != null
                && !downloadRequest.componentTypes().isEmpty()) {
            Set<String> requestedTypes = Set.copyOf(downloadRequest.componentTypes());
            List<PackComponent> filtered = version.getComponents().stream()
                    .filter(c -> requestedTypes.contains(c.getType().name()))
                    .toList();
            version.setComponents(filtered);
        }

        // Record the download
        HubApiKey apiKey = extractApiKey(authentication);
        PackDownloadRecord record = new PackDownloadRecord();
        record.setPackId(pack.getId());
        record.setVersionNumber(versionNumber);
        record.setTenantName(authentication.getName());
        record.setApiKeyPrefix(apiKey != null ? apiKey.getKeyPrefix() : "unknown");
        record.setDownloadedAt(Instant.now());
        record.setComponentsDownloaded(
                version.getComponents().stream()
                        .map(c -> c.getType().name())
                        .collect(Collectors.toList())
        );
        downloadRecordRepository.save(record);

        // Increment download count on pack
        pack.setDownloadCount(pack.getDownloadCount() + 1);
        packRepository.save(pack);

        // Increment downloads this month on API key
        if (apiKey != null) {
            apiKey.setDownloadsThisMonth(apiKey.getDownloadsThisMonth() + 1);
        }

        log.info("Pack downloaded: {} v{} by {}", pack.getName(), versionNumber, authentication.getName());
        return ResponseEntity.ok(version);
    }

    @GetMapping("/{slug}/reviews")
    public ResponseEntity<Page<PackReview>> getReviews(
            @PathVariable String slug,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        GovernancePack pack = packService.getPackDetail(slug);
        Page<PackReview> reviews = reviewRepository.findByPackIdOrderByCreatedAtDesc(pack.getId(), pageable);
        return ResponseEntity.ok(reviews);
    }

    @PostMapping("/{slug}/reviews")
    public ResponseEntity<PackReview> submitReview(
            @PathVariable String slug,
            @RequestBody ReviewRequest reviewRequest,
            Authentication authentication) {

        if (reviewRequest.rating() < 1 || reviewRequest.rating() > 5) {
            return ResponseEntity.badRequest().build();
        }

        GovernancePack pack = packService.getPackDetail(slug);
        HubApiKey apiKey = extractApiKey(authentication);

        PackReview review = new PackReview();
        review.setPackId(pack.getId());
        review.setTenantName(authentication.getName());
        review.setApiKeyPrefix(apiKey != null ? apiKey.getKeyPrefix() : "unknown");
        review.setRating(reviewRequest.rating());
        review.setComment(reviewRequest.comment());
        review.setCreatedAt(Instant.now());

        PackReview saved = reviewRepository.save(review);

        // Update pack average rating
        List<PackReview> allReviews = reviewRepository.findByPackIdOrderByCreatedAtDesc(pack.getId());
        double avgRating = allReviews.stream()
                .mapToInt(PackReview::getRating)
                .average()
                .orElse(0.0);
        pack.setAverageRating(Math.round(avgRating * 10.0) / 10.0);
        pack.setReviewCount(allReviews.size());
        packRepository.save(pack);

        log.info("Review submitted for pack: {} by {} (rating: {})",
                pack.getName(), authentication.getName(), reviewRequest.rating());
        return ResponseEntity.ok(saved);
    }

    @GetMapping("/meta/jurisdictions")
    public ResponseEntity<List<Map<String, Object>>> getJurisdictions() {
        List<String> jurisdictions = packService.getDistinctJurisdictions();
        List<Map<String, Object>> result = jurisdictions.stream()
                .filter(j -> j != null && !j.isBlank())
                .map(j -> {
                    Map<String, Object> entry = new HashMap<>();
                    entry.put("name", j);
                    return entry;
                })
                .toList();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/meta/industries")
    public ResponseEntity<List<String>> getIndustries() {
        return ResponseEntity.ok(packService.getDistinctIndustries());
    }

    @GetMapping("/meta/regulations")
    public ResponseEntity<List<String>> getRegulations() {
        return ResponseEntity.ok(packService.getDistinctRegulations());
    }

    private HubApiKey extractApiKey(Authentication authentication) {
        if (authentication != null && authentication.getDetails() instanceof HubApiKey apiKey) {
            return apiKey;
        }
        return null;
    }

    public record DownloadRequest(List<String> componentTypes) {}

    public record ReviewRequest(int rating, String comment) {}
}
