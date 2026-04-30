package co.uk.wolfnotsheep.infrastructure.controllers.admin;

import co.uk.wolfnotsheep.infrastructure.services.DlqReplayService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Phase 2.3 plan item 4 — admin REST surface for DLQ replay.
 *
 * <p>{@code POST /api/admin/dlq/{queueName}/replay?max=N} drains up to
 * {@code N} messages (default 100) from the named DLQ and re-publishes
 * each to its original exchange + routing key (read from the
 * {@code x-death} metadata that RabbitMQ stamps on dead-lettered
 * messages).
 *
 * <p>The {@code queueName} is whitelisted by {@link DlqReplayService} —
 * arbitrary queues can't be drained through this endpoint.
 */
@RestController
@RequestMapping("/api/admin/dlq")
public class DlqReplayController {

    private static final Logger log = LoggerFactory.getLogger(DlqReplayController.class);
    private static final int DEFAULT_MAX = 100;
    private static final int HARD_CAP = 1000;

    private final DlqReplayService service;

    public DlqReplayController(DlqReplayService service) {
        this.service = service;
    }

    @PostMapping("/{queueName}/replay")
    public ResponseEntity<DlqReplayService.ReplayResult> replay(
            @PathVariable String queueName,
            @RequestParam(name = "max", required = false, defaultValue = "100") int max) {
        int bounded = Math.max(1, Math.min(max, HARD_CAP));
        log.info("admin DLQ replay request: queue={} max={}", queueName, bounded);
        try {
            DlqReplayService.ReplayResult result = service.replay(queueName, bounded);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            log.warn("admin DLQ replay rejected: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }
}
