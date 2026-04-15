package co.uk.wolfnotsheep.hub.app.controllers;

import co.uk.wolfnotsheep.hub.models.HubApiKey;
import co.uk.wolfnotsheep.hub.app.services.ApiKeyService;
import co.uk.wolfnotsheep.hub.app.services.ApiKeyService.GeneratedKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/hub/admin/api-keys")
public class ApiKeyAdminController {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyAdminController.class);

    private final ApiKeyService apiKeyService;

    public ApiKeyAdminController(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    @GetMapping
    public ResponseEntity<List<HubApiKey>> listKeys() {
        List<HubApiKey> keys = apiKeyService.listKeys();
        // Clear keyHash from response for security — only keyPrefix is shown
        keys.forEach(k -> k.setKeyHash(null));
        return ResponseEntity.ok(keys);
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> generateKey(@RequestBody GenerateKeyRequest request) {
        try {
            GeneratedKey generated = apiKeyService.generateKey(
                    request.tenantName(), request.tenantEmail(), request.permissions());

            Map<String, Object> response = new HashMap<>();
            response.put("key", generated.plaintextKey());
            response.put("id", generated.apiKey().getId());
            response.put("keyPrefix", generated.apiKey().getKeyPrefix());
            response.put("tenantName", generated.apiKey().getTenantName());
            response.put("message", "Store this key securely — it will not be shown again.");

            log.info("Admin generated API key for tenant: {}", request.tenantName());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<HubApiKey> updateKey(@PathVariable String id,
                                               @RequestBody UpdateKeyRequest request) {
        try {
            HubApiKey updated = apiKeyService.updateKey(id, request.active(),
                    request.rateLimit(), request.downloadQuota());
            updated.setKeyHash(null);
            log.info("Admin updated API key: {} (id: {})", updated.getKeyPrefix(), id);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<HubApiKey> revokeKey(@PathVariable String id) {
        try {
            HubApiKey revoked = apiKeyService.revokeKey(id);
            revoked.setKeyHash(null);
            log.info("Admin revoked API key: {} (id: {})", revoked.getKeyPrefix(), id);
            return ResponseEntity.ok(revoked);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    public record GenerateKeyRequest(String tenantName, String tenantEmail, List<String> permissions) {}

    public record UpdateKeyRequest(Boolean active, Integer rateLimit, Integer downloadQuota) {}
}
