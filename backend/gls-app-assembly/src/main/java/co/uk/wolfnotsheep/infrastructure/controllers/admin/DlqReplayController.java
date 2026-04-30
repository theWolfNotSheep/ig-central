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
 * Admin REST surface for DLQ replay (Phase 2.3 plan item 4).
 *
 * <p>{@code POST /api/admin/dlq/{queueName}/replay?max=N&dryRun=true|false}
 * drains up to {@code N} messages from the named DLQ. Real mode
 * re-publishes to the original exchange + routing key (read from
 * the {@code x-death} metadata) and acks; dry-run mode reads the
 * messages, includes them in the {@code preview} field of the
 * response, and nacks-with-requeue so nothing is consumed.
 *
 * <p>The {@code queueName} is whitelisted by {@link DlqReplayService} —
 * arbitrary queues can't be drained through this endpoint. A
 * per-queue ShedLock prevents concurrent drains; a 409 Conflict is
 * returned when another caller already holds the lock.
 */
@RestController
@RequestMapping("/api/admin/dlq")
public class DlqReplayController {

    private static final Logger log = LoggerFactory.getLogger(DlqReplayController.class);
    private static final int HARD_CAP = 1000;

    private final DlqReplayService service;

    public DlqReplayController(DlqReplayService service) {
        this.service = service;
    }

    @PostMapping("/{queueName}/replay")
    public ResponseEntity<DlqReplayService.ReplayResult> replay(
            @PathVariable String queueName,
            @RequestParam(name = "max", required = false, defaultValue = "100") int max,
            @RequestParam(name = "dryRun", required = false, defaultValue = "false") boolean dryRun) {
        int bounded = Math.max(1, Math.min(max, HARD_CAP));
        log.info("admin DLQ replay request: queue={} max={} dryRun={}", queueName, bounded, dryRun);
        try {
            DlqReplayService.ReplayResult result = dryRun
                    ? service.dryRun(queueName, bounded)
                    : service.replay(queueName, bounded);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            log.warn("admin DLQ replay rejected: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (DlqReplayService.ReplayInProgressException e) {
            log.warn("admin DLQ replay rejected — another caller has the lock: {}", e.getMessage());
            return ResponseEntity.status(409).build();
        }
    }
}
