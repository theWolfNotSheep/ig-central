package co.uk.wolfnotsheep.platform.config.controllers;

import co.uk.wolfnotsheep.platform.config.models.AppConfig;
import co.uk.wolfnotsheep.platform.config.services.AppConfigService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
public class PublicConfigController {

    private final AppConfigService configService;

    public PublicConfigController(AppConfigService configService) {
        this.configService = configService;
    }

    @GetMapping("/api/public/config")
    public ResponseEntity<Map<String, Object>> getPublicConfig(
            @RequestParam(required = false) String category) {

        List<AppConfig> configs = (category != null && !category.isBlank())
                ? configService.getByCategory(category)
                : configService.getAll();

        Map<String, Object> result = new HashMap<>();
        for (AppConfig config : configs) {
            result.put(config.getKey(), config.getValue());
        }

        return ResponseEntity.ok(result);
    }
}
