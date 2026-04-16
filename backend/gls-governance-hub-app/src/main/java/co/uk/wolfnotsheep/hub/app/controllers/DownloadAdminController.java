package co.uk.wolfnotsheep.hub.app.controllers;

import co.uk.wolfnotsheep.hub.models.PackDownloadRecord;
import co.uk.wolfnotsheep.hub.repositories.PackDownloadRecordRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/hub/admin/downloads")
public class DownloadAdminController {

    private final PackDownloadRecordRepository downloadRepo;

    public DownloadAdminController(PackDownloadRecordRepository downloadRepo) {
        this.downloadRepo = downloadRepo;
    }

    @GetMapping
    public ResponseEntity<Page<PackDownloadRecord>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String packId) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "downloadedAt"));
        Page<PackDownloadRecord> result = (packId != null && !packId.isBlank())
                ? downloadRepo.findByPackIdOrderByDownloadedAtDesc(packId, pageable)
                : downloadRepo.findAll(pageable);
        return ResponseEntity.ok(result);
    }
}
