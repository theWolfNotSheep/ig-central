package co.uk.wolfnotsheep.infrastructure.controllers.admin;

import co.uk.wolfnotsheep.infrastructure.services.AuditCollectorClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/**
 * Phase 3 — admin proxy in front of {@code gls-audit-collector}'s
 * read surface. The collector itself runs on an internal Docker
 * hostname (see {@code gls.audit.collector.url}) and has no Spring
 * Security on the public surface — exposing it directly to browsers
 * would skip JWT + admin checks. This controller forwards a curated
 * subset of {@code /v1/events} from inside {@code /api/admin/**},
 * which the existing security filter chain already gates to admin
 * principals.
 *
 * <p>Two endpoints are surfaced:
 * <ul>
 *   <li>{@code GET /api/admin/audit-events/v2} — Tier 2 search.
 *       Forwards filters {@code documentId}, {@code eventType},
 *       {@code actorService}, {@code from}, {@code to},
 *       {@code pageToken}, {@code pageSize}. Returns the upstream
 *       JSON envelope verbatim.
 *   </li>
 *   <li>{@code GET /api/admin/audit-events/v2/{eventId}} — single
 *       event lookup; consults Tier 1 + Tier 2 (the collector
 *       resolves the storage layer). 404 on miss.
 *   </li>
 * </ul>
 *
 * <p>The body is forwarded as a raw JSON string to avoid coupling
 * this module to the collector's generated DTOs — that contract has
 * its own version cadence.
 */
@RestController
@RequestMapping("/api/admin/audit-events")
public class AuditExplorerController {

    private static final Logger log = LoggerFactory.getLogger(AuditExplorerController.class);

    private final AuditCollectorClient client;

    public AuditExplorerController(AuditCollectorClient client) {
        this.client = client;
    }

    @GetMapping(value = "/v2", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> searchTier2(
            @RequestParam(required = false) String documentId,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String actorService,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String pageToken,
            @RequestParam(required = false) Integer pageSize) {
        try {
            String body = client.listTier2Events(new AuditCollectorClient.SearchParams(
                    documentId, eventType, actorService, from, to, pageToken, pageSize));
            return ResponseEntity.ok(body);
        } catch (AuditCollectorClient.AuditCollectorException e) {
            log.warn("audit-collector list proxy failed: {}", e.getMessage());
            return ResponseEntity.status(502).body(
                    "{\"error\":\"audit-collector unavailable\",\"detail\":\""
                            + escapeJson(e.getMessage()) + "\"}");
        }
    }

    @GetMapping(value = "/v2/{eventId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getEvent(@PathVariable String eventId) {
        try {
            Optional<String> body = client.findEventById(eventId);
            return body.map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.status(404).body(
                            "{\"error\":\"event not found\",\"eventId\":\"" + escapeJson(eventId) + "\"}"));
        } catch (AuditCollectorClient.AuditCollectorException e) {
            log.warn("audit-collector get proxy failed: {}", e.getMessage());
            return ResponseEntity.status(502).body(
                    "{\"error\":\"audit-collector unavailable\",\"detail\":\""
                            + escapeJson(e.getMessage()) + "\"}");
        }
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", " ").replace("\r", " ");
    }
}
