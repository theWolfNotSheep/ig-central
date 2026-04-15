package co.uk.wolfnotsheep.infrastructure.controllers.admin;

import co.uk.wolfnotsheep.document.models.AiUsageLog;
import co.uk.wolfnotsheep.document.repositories.AiUsageLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/**
 * Admin endpoints for viewing AI usage logs — every LLM interaction
 * in the system with full prompt, context, and response audit trail.
 */
@RestController
@RequestMapping("/api/admin/ai")
public class AiUsageController {

    private final AiUsageLogRepository logRepo;

    public AiUsageController(AiUsageLogRepository logRepo) {
        this.logRepo = logRepo;
    }

    @GetMapping("/usage")
    public ResponseEntity<Page<AiUsageLog>> listUsage(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String documentId,
            @RequestParam(required = false) String triggeredBy,
            Pageable pageable) {
        if (documentId != null && !documentId.isBlank()) {
            return ResponseEntity.ok(logRepo.findByDocumentIdOrderByTimestampDesc(documentId, pageable));
        }
        if (triggeredBy != null && !triggeredBy.isBlank()) {
            return ResponseEntity.ok(logRepo.findByTriggeredByOrderByTimestampDesc(triggeredBy, pageable));
        }
        if (type != null && !type.isBlank() && status != null && !status.isBlank()) {
            return ResponseEntity.ok(logRepo.findByUsageTypeAndStatusOrderByTimestampDesc(type, status, pageable));
        }
        if (type != null && !type.isBlank()) {
            return ResponseEntity.ok(logRepo.findByUsageTypeOrderByTimestampDesc(type, pageable));
        }
        if (status != null && !status.isBlank()) {
            return ResponseEntity.ok(logRepo.findByStatusOrderByTimestampDesc(status, pageable));
        }
        return ResponseEntity.ok(logRepo.findByOrderByTimestampDesc(pageable));
    }

    @GetMapping("/usage/{id}")
    public ResponseEntity<AiUsageLog> getUsageDetail(@PathVariable String id) {
        return logRepo.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/usage/stats")
    public ResponseEntity<Map<String, Object>> usageStats() {
        Instant last24h = Instant.now().minus(24, ChronoUnit.HOURS);
        Instant last7d = Instant.now().minus(7, ChronoUnit.DAYS);

        return ResponseEntity.ok(Map.of(
                "total", logRepo.count(),
                "last24h", logRepo.countByTimestampAfter(last24h),
                "last7d", logRepo.countByTimestampAfter(last7d),
                "classifications", logRepo.countByUsageType("CLASSIFY"),
                "schemaSuggestions", logRepo.countByUsageType("SUGGEST_SCHEMA"),
                "schemaTests", logRepo.countByUsageType("TEST_SCHEMA"),
                "blockImprovements", logRepo.countByUsageType("IMPROVE_BLOCK"),
                "failures", logRepo.countByStatus("FAILED")
        ));
    }

    /**
     * Internal endpoint — called by services to log AI usage.
     * Not exposed to the frontend directly.
     */
    @PostMapping("/usage")
    public ResponseEntity<AiUsageLog> logUsage(@RequestBody AiUsageLog log) {
        return ResponseEntity.ok(logRepo.save(log));
    }
}
