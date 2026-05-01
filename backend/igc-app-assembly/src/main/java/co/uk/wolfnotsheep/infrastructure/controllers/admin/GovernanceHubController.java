package co.uk.wolfnotsheep.infrastructure.controllers.admin;

import co.uk.wolfnotsheep.infrastructure.services.GovernanceHubClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Proxies requests to the Governance Hub and handles pack imports.
 * The frontend calls this controller — it adds the API key and forwards to the hub.
 */
@RestController
@RequestMapping("/api/admin/governance-hub")
public class GovernanceHubController {

    private static final Logger log = LoggerFactory.getLogger(GovernanceHubController.class);

    private final GovernanceHubClient hubClient;

    public GovernanceHubController(GovernanceHubClient hubClient) {
        this.hubClient = hubClient;
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(Map.of("configured", hubClient.isConfigured()));
    }

    @GetMapping("/packs")
    public ResponseEntity<String> searchPacks(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String jurisdiction,
            @RequestParam(required = false) String industry,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            StringBuilder path = new StringBuilder("/api/hub/packs?page=" + page + "&size=" + size);
            if (q != null && !q.isBlank()) path.append("&q=").append(q);
            if (jurisdiction != null) path.append("&jurisdiction=").append(jurisdiction);
            if (industry != null) path.append("&industry=").append(industry);
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(hubClient.get(path.toString()));
        } catch (Exception e) {
            log.error("Hub search failed: {}", e.getMessage());
            return ResponseEntity.internalServerError().body("{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    @GetMapping("/packs/{slug}")
    public ResponseEntity<String> packDetail(@PathVariable String slug) {
        try {
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                    .body(hubClient.get("/api/hub/packs/" + slug));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    @GetMapping("/packs/{slug}/versions")
    public ResponseEntity<String> packVersions(@PathVariable String slug) {
        try {
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                    .body(hubClient.get("/api/hub/packs/" + slug + "/versions"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    @GetMapping("/packs/{slug}/versions/{version}")
    public ResponseEntity<String> packVersion(@PathVariable String slug, @PathVariable int version) {
        try {
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                    .body(hubClient.get("/api/hub/packs/" + slug + "/versions/" + version));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    @PostMapping("/packs/{slug}/versions/{version}/download")
    public ResponseEntity<String> downloadPack(@PathVariable String slug, @PathVariable int version,
                                                @RequestBody(required = false) String body) {
        try {
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                    .body(hubClient.post("/api/hub/packs/" + slug + "/versions/" + version + "/download", body));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    @GetMapping("/meta/jurisdictions")
    public ResponseEntity<String> jurisdictions() {
        try {
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                    .body(hubClient.get("/api/hub/packs/meta/jurisdictions"));
        } catch (Exception e) {
            return ResponseEntity.ok("{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    @GetMapping("/meta/industries")
    public ResponseEntity<String> industries() {
        try {
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                    .body(hubClient.get("/api/hub/packs/meta/industries"));
        } catch (Exception e) {
            return ResponseEntity.ok("{\"error\":\"" + e.getMessage() + "\"}");
        }
    }
}
