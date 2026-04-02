package co.uk.wolfnotsheep.platform.config.controllers;

import co.uk.wolfnotsheep.platform.config.models.AppConfig;
import co.uk.wolfnotsheep.platform.config.services.AppConfigService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/config")
@PreAuthorize("hasRole('ADMIN')")
public class AdminConfigController {

    private final AppConfigService configService;

    public AdminConfigController(AppConfigService configService) {
        this.configService = configService;
    }

    @GetMapping
    public ResponseEntity<List<AppConfig>> getAll(@RequestParam(required = false) String category) {
        List<AppConfig> configs = (category != null && !category.isBlank())
                ? configService.getByCategory(category)
                : configService.getAll();
        return ResponseEntity.ok(configs);
    }

    @PutMapping
    public ResponseEntity<AppConfig> upsert(@RequestBody UpsertRequest request) {
        AppConfig saved = configService.save(
                request.key(), request.category(), request.value(), request.description());
        return ResponseEntity.ok(saved);
    }

    @PostMapping("/refresh")
    public ResponseEntity<Map<String, String>> refresh() {
        configService.refresh();
        return ResponseEntity.ok(Map.of("status", "refreshed"));
    }

    public record UpsertRequest(
            String key,
            String category,
            Object value,
            String description
    ) {}
}
