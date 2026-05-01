package co.uk.wolfnotsheep.infrastructure.controllers.admin;

import co.uk.wolfnotsheep.infrastructure.services.AuditCollectorClient;
import co.uk.wolfnotsheep.infrastructure.services.AuditCsvExporter;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;

/**
 * Phase 3 — admin proxy in front of {@code igc-audit-collector}'s
 * read surface. The collector itself runs on an internal Docker
 * hostname (see {@code igc.audit.collector.url}) and has no Spring
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
    private final AuditCsvExporter csvExporter;

    public AuditExplorerController(AuditCollectorClient client, AuditCsvExporter csvExporter) {
        this.client = client;
        this.csvExporter = csvExporter;
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

    /**
     * Stream the Tier 2 search results matching the supplied filters as
     * a CSV download. Paginates through the audit collector internally,
     * writes a flat 16-column projection of the envelope, applies a hard
     * cap (default 10000, max 50000) to keep memory bounded.
     *
     * <p>Filename includes a UTC timestamp + the hard-cap-hit hint so
     * operators can spot when their filters were too broad. {@code 200}
     * with a partial payload on cap; {@code 502} on collector failure.
     */
    @GetMapping(value = "/v2/export.csv", produces = "text/csv")
    public void exportTier2Csv(
            @RequestParam(required = false) String documentId,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String actorService,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "10000") int hardCap,
            HttpServletResponse response) throws IOException {
        int cap = Math.max(1, Math.min(hardCap, 50_000));
        AuditCollectorClient.SearchParams base = new AuditCollectorClient.SearchParams(
                documentId, eventType, actorService, from, to, null, null);
        String filename = "audit-tier2-" + Instant.now().toString().replace(":", "-") + ".csv";
        response.setContentType("text/csv; charset=utf-8");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + filename + "\"");
        try (PrintWriter writer = response.getWriter()) {
            AuditCsvExporter.ExportResult result = csvExporter.export(base, cap, writer);
            if (result.hitCap()) {
                writer.write("# hit hard cap of " + cap + " events — narrow filters for a complete export\n");
            }
        } catch (AuditCollectorClient.AuditCollectorException e) {
            log.warn("audit-collector csv-export proxy failed: {}", e.getMessage());
            response.setStatus(502);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(
                    "{\"error\":\"audit-collector unavailable\",\"detail\":\""
                            + escapeJson(e.getMessage()) + "\"}");
        }
    }

    /**
     * List Tier 1 events for a resource — the per-resource compliance
     * timeline. Forwards to the collector's
     * {@code GET /v1/resources/{type}/{id}/events}.
     */
    @GetMapping(value = "/v2/resources/{resourceType}/{resourceId}/events",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> listTier1ForResource(
            @PathVariable String resourceType,
            @PathVariable String resourceId) {
        try {
            Optional<String> body = client.listTier1ForResource(resourceType, resourceId);
            return body.map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.status(404).body(
                            "{\"error\":\"no Tier 1 events for resource\",\"resourceType\":\""
                                    + escapeJson(resourceType) + "\",\"resourceId\":\""
                                    + escapeJson(resourceId) + "\"}"));
        } catch (AuditCollectorClient.AuditCollectorException e) {
            log.warn("audit-collector tier1-list proxy failed: {}", e.getMessage());
            return ResponseEntity.status(502).body(
                    "{\"error\":\"audit-collector unavailable\",\"detail\":\""
                            + escapeJson(e.getMessage()) + "\"}");
        }
    }

    /**
     * Tier 1 hash-chain verification for a resource. Forwards to the
     * collector's {@code GET /v1/chains/{resourceType}/{resourceId}/verify}.
     * Returns 200 + upstream JSON on success, 404 when no Tier 1 events
     * exist for that resource, 502 on transport failure.
     */
    @GetMapping(value = "/v2/chains/{resourceType}/{resourceId}/verify",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> verifyChain(
            @PathVariable String resourceType,
            @PathVariable String resourceId) {
        try {
            Optional<String> body = client.verifyChain(resourceType, resourceId);
            return body.map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.status(404).body(
                            "{\"error\":\"no Tier 1 events for resource\",\"resourceType\":\""
                                    + escapeJson(resourceType) + "\",\"resourceId\":\""
                                    + escapeJson(resourceId) + "\"}"));
        } catch (AuditCollectorClient.AuditCollectorException e) {
            log.warn("audit-collector chain-verify proxy failed: {}", e.getMessage());
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
